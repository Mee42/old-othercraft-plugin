#!/usr/bin/env bash
BACKUP=$(date +%F_%H-%M-%S).tar.gz
echo "tarring"
time tar zcf "backups/$BACKUP" memory/*
echo "copying"
time cp -r memory/* disk/
