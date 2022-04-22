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
Filename: "{app}\\bin\\underscorebackup-gui.exe"; Flags: shellexec runasoriginaluser

[Files]
Source: "..\\build\\launch4j\\underscorebackup.exe"; DestDir: "{app}\\bin"; Flags: ignoreversion
Source: "..\\build\\launch4j\\underscorebackup-gui.exe"; DestDir: "{app}\\bin"; Flags: ignoreversion
Source: "..\\build\\launch4j\\lib\\*"; DestDir: "{app}\\bin\\lib"; Flags: ignoreversion
Source: "..\\build\\jre\\*"; DestDir: "{app}\\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

[icons]
Name: "{commonstartup}\\Underscore Backup"; Filename: "{app}\\bin\\underscorebackup-gui.exe";
Name: "{commonprograms}\\Underscore Backup"; Filename: "{app}\\bin\\underscorebackup-gui.exe";

[Registry]
Root: "HKLM"; Subkey: "SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment"; ValueType: expandsz; ValueName: "Path"; ValueData: "{olddata};{app}\\bin" ; Check: NeedsAddPath('{app}\\bin')

[Code]

const
  EnvironmentKey = 'SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment';

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
    RemovePath(ExpandConstant('{app}\\bin'));
  end;
end;