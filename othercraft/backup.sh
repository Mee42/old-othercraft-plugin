#!/usr/bin/env bash
BACKUP=$(date +%F_%H-%M-%S).tar.gz
tar zcvf "backups/$BACKUP" memory/*
cp -r memory/* disk/