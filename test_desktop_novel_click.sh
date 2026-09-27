#!/bin/bash
set -e

# Kill any existing process on display :99
pkill -f "desktopApp" || true
pkill -f "Xvfb :99" || true
sleep 1

# Start Xvfb
Xvfb :99 -screen 0 1024x768x24 > /dev/null 2>&1 &
export DISPLAY=:99
sleep 1

# Launch Desktop App
./gradlew :desktopApp:run > /tmp/desktop_click.log 2>&1 &
APP_PID=$!

echo "Waiting for app window..."
for i in {1..30}; do
    if xdotool search --onlyvisible --class "lnreader-desktop-Main" > /dev/null 2>&1 || xdotool search --onlyvisible --name "lnreader" > /dev/null 2>&1; then
        echo "Window found!"
        break
    fi
    sleep 1
done

sleep 5

# Click on the source card (AllNovel)
xdotool mousemove 100 200 click 1
sleep 4

# Now click on a novel card (e.g. Reincarnation of The Strongest Sword God at x=100, y=400)
xdotool mousemove 100 400 click 1
sleep 6

mkdir -p /home/jules/verification
scrot /home/jules/verification/desktop_novel_detail_fixed.png

kill $APP_PID 2>/dev/null || true
pkill -f "Xvfb :99" || true
