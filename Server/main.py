import multiprocessing

from djitellopy import Tello
from flask import Flask, request, jsonify
from multiprocessing import Process

def runTello(queue):
    try:
        telloDrone = Tello(host="192.168.10.1")
        telloDrone.connect()

        telloDrone.for_back_velocity = 0
        telloDrone.left_right_velocity = 0
        telloDrone.up_down_velocity = 0
        telloDrone.yaw_velocity = 0
        telloDrone.speed = 0

        print('Drone Battery Percentage : ' + str(telloDrone.get_battery()) + '%')

        telloDrone.streamoff()

        isFlying = False
        while True:
            if queue:
                cmd = list(map(int, queue.pop(0).split(';')))
                print(cmd)

                if cmd[0] == 0:
                    if not isFlying:
                        telloDrone.takeoff()
                        isFlying = True
                elif cmd[0] == 1:
                    if isFlying:
                        telloDrone.land()
                        isFlying = False
                elif cmd[0] == 2:
                    if isFlying:
                        telloDrone.move_forward(cmd[1])
                elif cmd[0] == 3:
                    if isFlying:
                        telloDrone.move_back(cmd[1])
                elif cmd[0] == 4:
                    if isFlying:
                        telloDrone.move_left(cmd[1])
                elif cmd[0] == 5:
                    if isFlying:
                        telloDrone.move_right(cmd[1])
                elif cmd[0] == 6:
                    if isFlying:
                        telloDrone.rotate_clockwise(cmd[1])
                elif cmd[0] == 7:
                    if isFlying:
                        telloDrone.rotate_counter_clockwise(cmd[1])
                elif cmd[0] == 8:
                    if isFlying:
                        telloDrone.emergency()
    except Exception as e:
        print('Error!', e)


# Flask 서버
def runFlask(queue):
    app = Flask(__name__)

    # 이륙
    @app.route('/takeoff')
    def takeoff():
        queue.append('0;0')
        return jsonify(
            code=200,
            success=True,
            msg='OK'
        )

    # 착륙
    @app.route('/land')
    def land():
        queue.append('1;0')

        return jsonify(
            code=200,
            success=True,
            msg='OK'
        )

    # 전진
    @app.route('/forward')
    def moveForward():
        # Request Data
        distance = request.args.get('distance', 20)
        queue.append('2;{}'.format(int(distance)))

        return jsonify(
            code=200,
            success=True,
            msg='OK'
        )

    # 후진
    @app.route('/back')
    def moveBack():
        distance = request.args.get('distance', 20)
        queue.append('3;{}'.format(int(distance)))

        return jsonify(
            code=200,
            success=True,
            msg='OK'
        )

    # 왼쪽
    @app.route('/left')
    def moveLeft():
        distance = request.args.get('distance', 20)
        queue.append('4;{}'.format(int(distance)))

        return jsonify(
            code=200,
            success=True,
            msg='OK'
        )

    # 오른쪽
    @app.route('/right')
    def moveRight():
        distance = request.args.get('distance', 20)
        queue.append('5;{}'.format(int(distance)))

        return jsonify(
            code=200,
            success=True,
            msg='OK'
        )

    # 회전 (CW)
    @app.route('/rotate_cw')
    def rotate_cw():
        angle = request.args.get('angle', 30)
        queue.append('6;{}'.format(int(angle)))
        return jsonify(
            code=200,
            success=True,
            msg='OK'
        )

    # 회전 (CCW)
    @app.route('/rotate_ccw')
    def rotate_ccw():
        angle = request.args.get('angle', 30)
        queue.append('7;{}'.format(int(angle)))
        return jsonify(
            code=200,
            success=True,
            msg='OK'
        )

    # 비상 정지
    @app.route('/emergency')
    def emergency():
        queue.append('8;0')
        return jsonify(
            code=200,
            success=True,
            msg='OK'
        )

    app.run(host="0.0.0.0", port=8921)

if __name__ == '__main__':
    print('Tello Controller Server is Online!')

    # 명령 Queue를 위한 MultiProcessing Manager
    manager = multiprocessing.Manager()

    # 명령 Queue
    queueList = manager.list()

    # 드론 Process
    telloProcess = Process(target=runTello, args=[queueList])

    # Flask Process
    flaskProcess = Process(target=runFlask, args=[queueList])

    flaskProcess.start()
    telloProcess.start()

    flaskProcess.join()
    telloProcess.join()
