//
//  AppDelegate.swift
//  Underscore Backup
//
//  Created by Johnson, Henrik on 5/2/22.
//

import Cocoa
import UserNotifications

@main
class AppDelegate: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate {
    @IBOutlet var window: NSWindow!
    var statusItem: NSStatusItem?
        
    @IBOutlet weak var menu: NSMenu?
    @IBOutlet weak var configureItem: NSMenuItem?
    @IBOutlet weak var pauseItem: NSMenuItem?
    var url: URL?

    override func awakeFromNib() {
        super.awakeFromNib()

        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        statusItem?.button?.title = "Underscore Backup"
        statusItem?.menu = menu;
        
        let itemImage = NSImage(named: "trayicon")
        itemImage?.isTemplate = true
        statusItem?.button?.image = itemImage
        
        let homeDir = FileManager.default.urls(for: .applicationDirectory, in: .userDomainMask);
        for dir in homeDir {
            let path = dir.appendingPathComponent("configuration.url");
            do {
                let contents = try String(contentsOf: path, encoding: .utf8);
                url = URL(string: contents.trimmingCharacters(in: .whitespacesAndNewlines));
                if (url != nil) {
                    break;
                }
            } catch {
            }
        }

        if (url != nil) {
            Timer.scheduledTimer(timeInterval: 1.0, target: self, selector: #selector(fireTimer), userInfo: nil, repeats: true)
        } else {
            NSApp.terminate(self);
        }
    }
    
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(NSApplication.ActivationPolicy.prohibited);
        
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert]) { granted, error in
        }
    }
    
    func applicationSupportsSecureRestorableState(_ app: NSApplication) -> Bool {
        return true
    }
    
    @IBAction func configure(_ sender: Any) {
        if let useUrl = url {
          NSWorkspace.shared.open(useUrl)
        }
    }

    @objc func fireTimer() {
        if let url = url {
            let task = URLSession.shared.dataTask(with: url.deletingLastPathComponent()) { data, response, error in
                if error != nil {
                    DispatchQueue.main.async {
                        NSApp.terminate(self);
                    }
                }
            }
            task.resume()
        }

        let homeDir = FileManager.default.urls(for: .applicationDirectory, in: .userDomainMask);
        for dir in homeDir {
            var path = dir.appendingPathComponent("notification");
            do {
                let contents = try String(contentsOf: path, encoding: .utf8);
                try FileManager.default.removeItem(at: path);
                displayNotification(error: false, message: contents);
            } catch {
            }
            path = dir.appendingPathComponent("error");
            do {
                let contents = try String(contentsOf: path, encoding: .utf8);
                try FileManager.default.removeItem(at: path);
                displayNotification(error: true, message: contents);
            } catch {
            }
        }
    }
    
    @IBAction func pauseItem(_ sender: Any) {
        if let url = url {
            let task = URLSession.shared.dataTask(with: url.appendingPathComponent("api/backup/pause"));
            task.resume()
        }
    }
    
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        DispatchQueue.main.async {
            self.configure(self);
        }
    }
    
    func displayNotification(error: Bool, message: String) {
        let center = UNUserNotificationCenter.current();

        center.delegate = self;
        
        let content = UNMutableNotificationContent()
        if (error) {
            content.subtitle = "ERROR"
            content.body = message
        } else {
            content.body = message
        }

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.001, repeats: false)
        
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: trigger)
        center.add(request);
    }
}

