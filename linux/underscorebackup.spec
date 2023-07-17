Name:           underscorebackup
Version:        VERSION
Release:        1
Summary:        Serverless backup solution with client side encryption.
License:        GPL
URL:            https://github.com/UnderscoreResearch/UnderscoreBackup
AutoReq:        no
Source0:        underscorebackup.tar.gz

%description
Serverless backup solution with client side encryption.

%prep
%setup -q
%build

%install
mkdir -p $RPM_BUILD_ROOT
cp -r . $RPM_BUILD_ROOT

%files
/etc/systemd/system/underscorebackup.service
/opt/underscorebackup/*

%preun

systemctl stop underscorebackup.service
systemctl disable underscorebackup.service

if grep -E "^java-options=" /opt/underscorebackup/lib/app/underscorebackup.cfg 2>&1 > /dev/null
then
  grep -E "^java-options=" /opt/underscorebackup/lib/app/underscorebackup.cfg > /etc/underscorebackup/javaoptions.cfg
fi

%post

JAVA_OPTIONS=/etc/underscorebackup/javaoptions.cfg
APP_CONFIG=/opt/underscorebackup/lib/app/underscorebackup.cfg

if [ -f $JAVA_OPTIONS ]
then
  DATA=`grep -v -E "^java-options=" $APP_CONFIG ; cat $JAVA_OPTIONS`
  echo "$DATA" > $APP_CONFIG
  if ! grep -E "^java-options=--add-opens=java.base/java.nio=ALL-UNNAMED" $JAVA_OPTIONS > /dev/null
  then
    echo "java-options=--add-opens=java.base/java.nio=ALL-UNNAMED" >> $APP_CONFIG
  fi
  if ! grep -E "^java-options=--add-opens=java.base/sun.nio.ch=ALL-UNNAMED" $JAVA_OPTIONS > /dev/null
  then
    echo "java-options=--add-opens=java.base/sun.nio.ch=ALL-UNNAMED" >> $APP_CONFIG
  fi
fi

systemctl daemon-reload
systemctl start underscorebackup.service
systemctl enable underscorebackup.service

for a in 1 2 3 4 5 6 7 8 9 10
do
  if [ -f /var/cache/underscorebackup/configuration.url ]
  then
    break
  fi
  sleep 1
done

if [ -f /var/cache/underscorebackup/configuration.url ]
then
  echo "Go to the URL below to configure underscore backup"
  echo
  cat /var/cache/underscorebackup/configuration.url
  echo
  echo "At any point you can get the configuration interface by executing: underscorebackup configure"
else
  echo "Failed to start daemon"
fi