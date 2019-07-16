#!/usr/bin/env bash

FILE="memory/FILE"
if [[ -f ${FILE} ]]; then
   echo "File $FILE exists."
else
   echo "File $FILE does not exist."
   rsync -rtvu disk/ memory/
fi

cd memory/

java -jar *.jar

cd ..

./backup.sh