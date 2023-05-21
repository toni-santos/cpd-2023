#!/bin/bash

if [ $# -eq 0 ]; then
    echo "Usage: run.sh <number-of-clients>"
    exit 1
fi

number=$1

re='^[0-9]+$'
if ! [[ $number =~ $re ]]; then
    echo "Number of clients must be a valid integer"
    exit 1
fi


for ((i=0; i < number; i++))
do
    if [ "$i" -eq 0 ]; then
        mode="Server"
    else
        mode="Client"
    fi

    command="gradle $mode"

    if command -v gnome-terminal &> /dev/null; then
        gnome-terminal -- /bin/bash -c "$command"
    elif command -v konsole &> /dev/null; then
        konsole --hold -e sh -c "$command"
    elif command -v xfce4-terminal &> /dev/null; then
        xfce4-terminal --hold --execute sh -c "$command"
    elif command -v terminator &> /dev/null; then
        terminator -e "sh -c '$command'"
    elif command -v tilix &> /dev/null; then
        tilix -e "sh -c '$command'"
    elif command -v alacritty &> /dev/null; then
        alacritty -e sh -c "$command"
    elif command -v open &> /dev/null; then
        open -a Terminal -n -e "$command"
    elif command -v iterm2 &> /dev/null; then
        osascript -e "tell application \"iTerm\" to create window with default profile" -e "tell current session of current window to write text \"$command\""
    else
        echo "No supported terminal emulator found."
    fi
done