//
//  AppDelegate.swift
//  Underscore Backup
//
//  Created by Johnson, Henrik on 5/2/22.
//

import Cocoa
import UserNotifications
import SwiftCPUDetect

@main
class AppDelegate: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate {
    let launchctl = "/bin/launchctl";
    @IBOutlet var window: NSWindow!
    var statusItem: NSStatusItem?

    @IBOutlet weak var menu: NSMenu?
    @IBOutlet weak var configureItem: NSMenuItem?
    @IBOutlet weak var pauseItem: NSMenuItem?
    @IBOutlet weak var autostartItem: NSMenuItem?
    
    var readUrl: URL?
    var url: URL?
    var agentUrl: URL?

    override func awakeFromNib() {
        let args = CommandLine.arguments;
        
        if (args.last == "autostart") {
            let task = Process()
            task.executableURL = URL(fileURLWithPath: executablePath())
            do {
                try task.run();
            } catch {
                let alert = NSAlert()

                alert.messageText = "Failed to auto start application"
                alert.informativeText = "Application failed to launch. Error message was \(error)"
                alert.addButton(withTitle: "OK")
                alert.alertStyle = .critical

                alert.runModal()
            }
            
            NSApp.terminate(self);
        } else {
            if (!createLock()) {
                NSApp.terminate(self);
                return;
            }

            agentUrl = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/LaunchAgents/com.underscoreresearch.UnderscoreBackup.plist");

            statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
            statusItem?.menu = menu
            
            let itemImage = NSImage(named: "trayicon")
            itemImage?.isTemplate = true
            statusItem?.button?.image = itemImage
            
            launchApplication()
            Timer.scheduledTimer(timeInterval: 1.0, target: self, selector: #selector(fireTimer), userInfo: nil, repeats: true)
        }
    }
    
    func createLock() -> Bool {
        let notificationsDir = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent(".underscoreBackup/notifications");
        do {
            if !FileManager.default.fileExists(atPath: notificationsDir.path) {
                try FileManager.default.createDirectory(atPath: notificationsDir.path, withIntermediateDirectories: true, attributes: nil)
            }
        } catch {
            let alert = NSAlert()

            alert.messageText = "Failed to create notifications path"
            alert.informativeText = "Failed to create notifications path"
            alert.addButton(withTitle: "OK")
            alert.alertStyle = .critical

            alert.runModal()

            return false;
        }
        
        let lockUrl = notificationsDir.appendingPathComponent("lock");
        let path = lockUrl.path;

        let fd = open(path, O_WRONLY|O_CREAT, 0o600)

        if fd < 0 {
            let alert = NSAlert()

            alert.messageText = "Failed to create lock file"
            alert.informativeText = "Could not create lock file"
            alert.addButton(withTitle: "OK")
            alert.alertStyle = .critical

            alert.runModal()
            return false;
        }
        
        if (lockf(fd, F_TLOCK, 0) == 0) {
            return true;
        }
        
        let alert = NSAlert()

        alert.messageText = "Can't lock file"
        alert.informativeText = "Could not lock file " + path + " (" + String(errno) + ")"
        alert.addButton(withTitle: "OK")
        alert.alertStyle = .critical

        alert.runModal()
        return false;
    }
    
    func architecture() -> String {
        SwiftCPUDetect.GeneralPrinter.enabled = false
        let architecture = CpuArchitecture.current()
        if (architecture == CpuArchitecture.intel64) {
            return "x86_64"
        }
        return "arm64"
    }
    
    func launchApplication() {
        let architecture = architecture()
        do {
            let path = "\(Bundle.main.bundlePath)/Contents/daemon/\(architecture)/bin/underscorebackup"
            let task = Process()
            task.executableURL = URL(fileURLWithPath: path)
            task.arguments = ["interactive", "lock"]
            try task.run()
        } catch {
            let alert = NSAlert()

            alert.messageText = "Failed to launch backup daemon"
            alert.informativeText = "Application failed to launch background daemon. Error message was \(error)"
            alert.addButton(withTitle: "OK")
            alert.alertStyle = .critical

            alert.runModal()
            NSApp.terminate(self)
        }
    }
    
    func executablePath() -> String {
        return "\(Bundle.main.bundlePath)/Contents/MacOS/Underscore Backup"
    }
    
    func registerIfNeeded() {
        let path = Bundle.main.path(forResource: "com.underscoreresearch.UnderscoreBackup", ofType: "plist")
        do {
            if let existingPath = path {
                let contents = try String(contentsOfFile: existingPath, encoding: .utf8)
                let newContents = contents.replacingOccurrences(of: "{ROOT}", with: executablePath())
                if let realUrl = agentUrl {
                    var start = false;
                    if (!FileManager.default.fileExists(atPath: realUrl.path)) {
                        start = true;
                    } else {
                        let oldData = try String(contentsOf: realUrl);
                        if (oldData == newContents) {
                            determineAutostart();
                            autostartItem?.isEnabled = true;
                            return;
                        }
                    }
                    if let data = newContents.data(using: .utf8) {
                        try data.write(to: realUrl)
                    }
                    
                    if (start) {
                        setAutoStart(true)
                    } else {
                        determineAutostart();
                    }
                }
            }
            autostartItem?.isEnabled = true;
        } catch {
            let alert = NSAlert()

            alert.messageText = "Failed to set up auto launch"
            alert.informativeText = "Failed to set up auto launch. Error message was \(error)"
            alert.addButton(withTitle: "OK")
            alert.alertStyle = .critical

            alert.runModal()
        }
    }
    
    func determineAutostart() {
        do {
            let task = Process()
            task.executableURL = URL(fileURLWithPath: launchctl)
            task.arguments = ["list", "com.underscoreresearch.UnderscoreBackup"];
            try task.run()
            task.waitUntilExit();
            let status = task.terminationStatus;
            if (status == 0) {
                autostartItem?.state = .on
            } else {
                autostartItem?.state = .off
            }
        } catch {
            autostartItem?.state = .off
        }
    }

    func setAutoStart(_ start: Bool) {
        do {
            if let realUrl = agentUrl {
                if (start) {
                    var task = Process()
                    task.executableURL = URL(fileURLWithPath: launchctl)
                    task.arguments = ["load", realUrl.path];
                    try task.run()
                    task.waitUntilExit();
                    
                    task = Process()
                    task.executableURL = URL(fileURLWithPath: launchctl)
                    task.arguments = ["start", "com.underscoreresearch.UnderscoreBackup.plist"];
                    try task.run()
                    task.waitUntilExit();
                    
                    autostartItem?.state = .on;
                } else {
                    var task = Process()
                    task.executableURL = URL(fileURLWithPath: launchctl)
                    task.arguments = ["stop", "com.underscoreresearch.UnderscoreBackup.plist"];
                    try task.run()
                    task.waitUntilExit();
                    
                    task = Process()
                    task.executableURL = URL(fileURLWithPath: launchctl)
                    task.arguments = ["unload", realUrl.path];
                    try task.run()
                    task.waitUntilExit();

                    autostartItem?.state = .off;
                }
            }
        } catch {
            let alert = NSAlert()

            alert.messageText = "Failed to change auto launch options"
            alert.informativeText = "Failed to change auto launch options. Error message was \(error)"
            alert.addButton(withTitle: "OK")
            alert.alertStyle = .critical

            alert.runModal()
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
    
    func determineUrl() {
        if (readUrl == nil) {
            let file = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent(".underscoreBackup/configuration.url")
            do {
                let contents = try String(contentsOf: file, encoding: .utf8);
                readUrl = URL(string: contents.trimmingCharacters(in: .whitespacesAndNewlines));
                if (readUrl != nil) {
                    registerIfNeeded()
                }
            } catch {
            }
        }
    }

    @objc func fireTimer() {
        determineUrl();
        
        if let url = readUrl {
            let task = URLSession.shared.dataTask(with: url) { data, response, error in
                if error != nil {
                    DispatchQueue.main.async {
                        self.readUrl = nil;
                        self.statusItem?.button?.toolTip = "Background service is not running";
                    }
                } else {
                    if self.url != self.readUrl {
                        DispatchQueue.main.async {
                            self.url = self.readUrl;
                        }
                    }
                }
            }
            task.resume()
        }

        let homeDir = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent(".underscoreBackup/notifications");
        var path = homeDir.appendingPathComponent("notification");
        do {
            let contents = try String(contentsOf: path, encoding: .utf8);
            try FileManager.default.removeItem(at: path);
            displayNotification(error: false, message: contents);
        } catch {
        }
        path = homeDir.appendingPathComponent("error");
        do {
            let contents = try String(contentsOf: path, encoding: .utf8);
            try FileManager.default.removeItem(at: path);
            displayNotification(error: true, message: contents);
        } catch {
        }
        path = homeDir.appendingPathComponent("tooltip");
        do {
            let contents = try String(contentsOf: path, encoding: .utf8);
            statusItem?.button?.toolTip = contents;
        } catch {
        }
    }
    
    @IBAction func quitItem(_ sender: Any) {
        NSApp.terminate(self)
    }
    
    @IBAction func autostartToggle(_ sender: Any) {
        setAutoStart(autostartItem?.state != .on)
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
        content.body = message
        if (error) {
            content.subtitle = "ERROR"
        }

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.001, repeats: false)
        
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: trigger)
        center.add(request);
    }
}

