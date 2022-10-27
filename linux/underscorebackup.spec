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

if [ -f /etc/underscorebackup/javaoptions.cfg ]
then
  DATA=`grep -v -E "^java-options=" /opt/underscorebackup/lib/app/underscorebackup.cfg ; cat /etc/underscorebackup/javaoptions.cfg`
  echo "$DATA" > /opt/underscorebackup/lib/app/underscorebackup.cfg
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