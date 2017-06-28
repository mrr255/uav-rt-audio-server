import time
import os
import datetime
from dronekit import connect, VehicleMode
import subprocess
import sys

user_input = [0,0]

#connection_string = "COM3" #for connection via USB
connection_string = "/dev/ttyACM0" #for connection via USB
print("Connecting to vehicle on: %s" % (connection_string,))
vehicle = connect(connection_string, wait_ready=True)

@vehicle.on_message('SYSTEM_TIME')
def listener(self, name, message):
    user_input[1] = message.time_unix_usec
    if user_input[1] == 0:
        user_input[0] += 1
        print "no gps"
    else:
        print message.time_unix_usec
	unixT = user_input[1] / 1000000.000000
        date_str = "@" + str(unixT);
        print(date_str);
        subprocess.call(["sudo","date", "+%s", "-s", date_str])
        print(time.time())
        print datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%S.%f-7:00')[:-3]

try:

    while user_input[1] == 0:
        if user_input[0] >10:
            break
        pass
    print(user_input[1])
    vehicle.close()
except KeyboardInterrupt:
    vehicle.close()
