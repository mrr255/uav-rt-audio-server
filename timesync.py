import time
import os
import datetime
from dronekit import connect, VehicleMode
import subprocess
import sys
import platform
global quitter
quitter = False
global cur_time
osName = platform.system()
if( osName == 'Windows'):
    connection_string = "COM3" #for connection via USB
else:
    connection_string = "/dev/ttyACM0" #for connection via USB
#print("Connecting to vehicle on: %s" % (connection_string,))
vehicle = connect(connection_string, wait_ready=False)

@vehicle.on_message('SYSTEM_TIME')
def listener(self, name, message):
    global quitter
    global cur_time
    cur_time = message.time_unix_usec
    quitter = True



try:

    while not quitter :
        pass
    #print(user_input[1])
    if cur_time != 0:
        #print(message.time_unix_usec)
        unixT = cur_time/1000000.000000
        date_str = "@" + str(unixT);
        #print(date_str);
        subprocess.call(["sudo","date", "+%s", "-s", date_str])

        #print(time.time())
    else:
        print("No gps")
    print(datetime.datetime.utcnow().strftime('%Y-%m-%dT%H-%M-%S_%f')[:-3])
    quit
except KeyboardInterrupt:
    quit
