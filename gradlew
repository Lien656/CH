#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
# Gradle start up script for POSIX
##############################################################################

app_path=$0
while [ -h "$app_path" ]; do
  ls=$( ls -ld "$app_path" )
  link=${ls#*' -> '}
  case $link in
    /*) app_path=$link ;;
    *) app_path=$APP_HOME$link ;;
  esac
done
APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${app_path%"${app_path##*/}"}" && pwd -P ) || exit

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

warn () { echo "$*" >&2; }
die () { echo; echo "$*"; echo; exit 1; }

# Determine Java
if [ -n "$JAVA_HOME" ]; then
  JAVACMD=$JAVA_HOME/bin/java
  [ ! -x "$JAVACMD" ] && die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
else
  JAVACMD=java
  command -v java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

DEFAULT_JVM_OPTS='-Dfile.encoding=UTF-8 "-Xmx64m" "-Xms64m"'
set -- \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"

eval "set -- $(
  printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
  xargs -n1 |
  sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
  tr '\n' ' '
)" '"$@"'

exec "$JAVACMD" "$@"
