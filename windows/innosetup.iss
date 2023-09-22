; Script generated by the Inno Script Studio Wizard.
; SEE THE DOCUMENTATION FOR DETAILS ON CREATING INNO SETUP SCRIPT FILES!

[Setup]
; NOTE: The value of AppId uniquely identifies this application.
; Do not use the same AppId value in installers for other applications.
; (To generate a new GUID, click Tools | Generate GUID inside the IDE.)
AppId={{24B75B96-9DF9-4702-8EFC-55C07858CBFA}
AppName=Underscore Backup
AppVersion=${applicationVersion}
;AppVerName=Underscore Backup ${applicationVersion}
AppPublisher=Underscore Research
AppPublisherURL=http://www.github.com/UnderscoreResearch/UnderscoreBackup
AppSupportURL=http://www.github.com/UnderscoreResearch/UnderscoreBackup
AppUpdatesURL=http://www.github.com/UnderscoreResearch/UnderscoreBackup
CloseApplications=no
ArchitecturesInstallIn64BitMode = x64
DefaultDirName={commonpf}\\Underscore Backup
DefaultGroupName=Underscore Backup
DisableProgramGroupPage=yes
LicenseFile=..\\LICENSE
OutputDir=..\\build\\distributions
OutputBaseFilename=underscorebackup-${applicationVersion}
Compression=lzma
SolidCompression=yes

[Run]
Filename: "{app}\\underscorebackup-gui.exe"; Flags: shellexec runasoriginaluser;

[Files]
Source: "..\\build\\installerimage\\underscorebackup.exe"; DestDir: "{app}"; Flags: ignoreversion; \\
    BeforeInstall: TaskKill('underscorebackup.exe')
Source: "..\\build\\installerimage\\underscorebackup-gui.exe"; DestDir: "{app}"; Flags: ignoreversion; \\
    BeforeInstall: TaskKill('underscorebackup-gui.exe')
Source: "..\\build\\installerimage\\README.md"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\\build\\installerimage\\app\\underscorebackup.cfg"; DestDir: "{app}\\app"; \\
    Flags: ignoreversion; AfterInstall: UpdateMainCfg
Source: "..\\build\\installerimage\\app\\underscorebackup-gui.cfg"; DestDir: "{app}\\app"; \\
    Flags: ignoreversion; AfterInstall: UpdateUICfg
Source: "..\\build\\installerimage\\app\\*.xml"; DestDir: "{app}\\app"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\\build\\installerimage\\app\\*.jar"; DestDir: "{app}\\app"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\\build\\installerimage\\runtime\\*"; DestDir: "{app}\\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "nssm.exe"; DestDir: "{app}"; Flags: ignoreversion;

[InstallDelete]
Type: filesandordirs; Name: {app}\\app
Type: filesandordirs; Name: {app}\\bin
Type: filesandordirs; Name: {app}\\lib
Type: filesandordirs; Name: {app}\\runtime

[icons]
Name: "{commonstartup}\\Underscore Backup"; Filename: "{app}\\underscorebackup-gui.exe";
Name: "{commonprograms}\\Underscore Backup"; Filename: "{app}\\underscorebackup-gui.exe";

[Registry]
Root: "HKLM"; Subkey: "SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment"; ValueType: expandsz; ValueName: "Path"; ValueData: "{olddata};{app}"; Check: NeedsAddPath('{app}')

[Code]

const
  EnvironmentKey = 'SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment';

var
  PreviousMemory: string;
  PreviousMemoryUI: string;
  TypePage: TInputOptionWizardPage;

function ServiceInstall: Boolean;
begin
  Result := TypePage.SelectedValueIndex = 0;
end;

function ApplicationInstall: Boolean;
begin
  Result := TypePage.SelectedValueIndex = 1;
end;

procedure InitializeWizard;
begin

  TypePage := CreateInputOptionPage(wpLicense,
    'Installation Type', 'How would you like to run the application?',
    'Select how you would like Underscore Backup to run.',
    True, False);
  TypePage.Add('Service. Run as service in the background on startup.');
  TypePage.Add('Application. Run as user application only when logged in.');

  case GetPreviousData('Type', '') of
    'application': TypePage.SelectedValueIndex := 1;
  else
    TypePage.SelectedValueIndex := 0;
  end;
end;

procedure RegisterPreviousData(PreviousDataKey: Integer);
var
  TypeMode: String;
begin
  if TypePage.SelectedValueIndex = 0 then
    TypeMode := 'service'
  else
    TypeMode := 'application';

  SetPreviousData(PreviousDataKey, 'Type', TypeMode);
end;

procedure TaskKill(FileName: String);
var
  ResultCode: Integer;
begin
   Exec('taskkill.exe', '/f /im ' + '"' + FileName + '"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
end;

procedure ShutdownApp();
var
  ResultCode: integer;
begin
  Exec(ExpandConstant('{app}\\nssm'), 'stop UnderscoreBackup', '', SW_HIDE, ewWaitUntilTerminated, ResultCode)
  Exec(ExpandConstant('{app}\\nssm'), 'remove UnderscoreBackup confirm', '', SW_HIDE, ewWaitUntilTerminated, ResultCode)
  Exec(ExpandConstant('{app}\\underscorebackup'), 'shutdown', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  TaskKill('underscorebackup.exe');
  TaskKill('underscorebackup-gui.exe');
end;

function ExtractMemory(file: string) : string;
var
  config : TArrayOfString;
  i : Integer;
  line : string;
begin
  if FileExists(file) then
  begin
    if LoadStringsFromFile(file, config) then
    begin
      for i := 0 to GetArrayLength(config) - 1 do
      begin
        line := config[i];
        if Pos('java-options=-Xmx', line) = 1 then
        begin
          Result := line;
          exit;
        end;
      end;
    end;
  end;
  Result := '';
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  PreviousMemory := ExtractMemory(ExpandConstant('{app}\\app\\underscorebackup.cfg'));
  PreviousMemoryUI := ExtractMemory(ExpandConstant('{app}\\app\\underscorebackup-gui.cfg'));

  ShutdownApp();
  Result := '';
end;

function NeedsAddPath(Param: string): boolean;
var
  OrigPath: string;
  ParamExpanded: string;
begin
  //expand the setup constants like {app} from Param
  ParamExpanded := ExpandConstant(Param);
  if not RegQueryStringValue(HKEY_LOCAL_MACHINE,
    EnvironmentKey,
    'Path', OrigPath)
  then begin
    Result := True;
    exit;
  end;
  // look for the path with leading and trailing semicolon and with or without \\ ending
  // Pos() returns 0 if not found
  Result := Pos(';' + UpperCase(ParamExpanded) + ';', ';' + UpperCase(OrigPath) + ';') = 0;  
  if Result = True then
     Result := Pos(';' + UpperCase(ParamExpanded) + '\\;', ';' + UpperCase(OrigPath) + ';') = 0;
end;

procedure RemovePath(Path: string);
var
  Paths: string;
  P: Integer;
begin
  if not RegQueryStringValue(HKEY_LOCAL_MACHINE, EnvironmentKey, 'Path', Paths) then
  begin
    Log('PATH not found');
  end
  else
  begin
    Log(Format('PATH is [%s]', [Paths]));

    P := Pos(';' + Uppercase(Path) + ';', ';' + Uppercase(Paths) + ';');
    if P = 0 then
    begin
      Log(Format('Path [%s] not found in PATH', [Path]));
    end
    else
    begin
      if P > 1 then P := P - 1;
      Delete(Paths, P, Length(Path) + 1);
      Log(Format('Path [%s] removed from PATH => [%s]', [Path, Paths]));

      if RegWriteStringValue(HKEY_LOCAL_MACHINE, EnvironmentKey, 'Path', Paths) then
      begin
        Log('PATH written');
      end
      else
      begin
        Log('Error writing PATH');
      end;
    end;
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
  begin
    ShutdownApp();
    RemovePath(ExpandConstant('{app}\\bin'));
    RemovePath(ExpandConstant('{app}'));
  end;
end;

procedure ReplaceMemory(file: string; newLine: string; service: boolean; debug: boolean);
var
  config : TArrayOfString;
  i : Integer;
  line : string;
begin
  if FileExists(file) then
  begin
    if LoadStringsFromFile(file, config) then
    begin
      for i := 0 to GetArrayLength(config) - 1 do
      begin
        line := config[i];
        if Pos('java-options=-Xmx', line) = 1 then
        begin
          config[i] := newLine;
        end;
        if service then
        begin
          if Pos('arguments=', line) = 1 then
          begin
            config[i] := 'arguments=gui';
          end;
        end;
      end;

      if debug then
      begin
        SetArrayLength(config, GetArrayLength(config) + 1);
        config[GetArrayLength(config) - 1] := 'java-options=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=54321';
      end;

      if not SaveStringsToFile(file, config, False) then
      begin
         MsgBox('Failed to save contents of ' + file, mbError, MB_OK);
      end;
      Log('Adjusted ' + file);
    end
    else
    begin
      MsgBox('Failed to read contents of ' + file, mbError, MB_OK);
    end;
  end
  else
  begin
    MsgBox('Failed to find file ' + file, mbError, MB_OK);
  end;
end;

procedure RunNssm(command: string);
var
  ResultCode: integer;
begin
  if Exec(ExpandConstant('{app}\\nssm'), command, '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
  begin
    if ResultCode <> 0 then
    begin
      MsgBox('Failed to install service on command'#13#10#13#10 + command + #13#10#13#10'Exit code ' + IntToStr(ResultCode),
             mbError, MB_OK);
    end;
  end
  else
  begin
    MsgBox('Failed to run NSSM'#13#10#13#10'Result code ' + IntToStr(ResultCode),
           mbError, MB_OK);
  end;
end;

procedure UpdateMainCfg();
begin
    if PreviousMemory = '' then
    begin
      PreviousMemoryUI := 'java-options=-Xmx256m';
    end;

    ReplaceMemory(ExpandConstant('{app}\\app\\underscorebackup.cfg'), PreviousMemory, false, false);
end;

procedure UpdateUICfg();
begin
    if PreviousMemoryUI = '' then
    begin
      if ServiceInstall() then
      begin
        PreviousMemoryUI := 'java-options=-Xmx32m';
      end
      else
      begin
        PreviousMemoryUI := 'java-options=-Xmx256m';
      end;
    end
    else if (PreviousMemoryUI = 'java-options=-Xmx32m') and ApplicationInstall() then
    begin
      PreviousMemoryUI := 'java-options=-Xmx256m';
    end;

    ReplaceMemory(ExpandConstant('{app}\\app\\underscorebackup-gui.cfg'), PreviousMemoryUI, ServiceInstall(), false);
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: integer;
begin
  if CurStep = ssPostInstall then
  begin
    if ServiceInstall() then
    begin
      RunNssm(ExpandConstant('install UnderscoreBackup "{app}\\underscorebackup.exe"'));
      RunNssm('set UnderscoreBackup AppParameters interactive service');
      RunNssm('set UnderscoreBackup Start SERVICE_AUTO_START');
      RunNssm('set UnderscoreBackup DisplayName Underscore Backup');
      RunNssm('set UnderscoreBackup Description Underscore Backup background service');
      Exec(ExpandConstant('{app}\\nssm'), 'start UnderscoreBackup', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    end;
  end;
end;
