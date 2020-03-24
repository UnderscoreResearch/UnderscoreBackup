#!/bin/sh

nice /opt/underscorebackup/bin/underscorebackup -k /etc/underscorebackup/key backup 2>&1 >> /var/log/underscorebackup.log
