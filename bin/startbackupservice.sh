#!/bin/sh

nice /opt/underscorebackup/bin/backup -k /etc/underscorebackup/key backup 2>&1 >> /var/log/underscorebackup.log
