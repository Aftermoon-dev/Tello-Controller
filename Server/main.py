import multiprocessing

from djitellopy import Tello
from flask import Flask, request, jsonify
from multiprocessing import Process

def runTello(queue, errorDict):
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
                # 비상 정지 최우선
                if "11;0" in queue:
                    telloDrone.emergency()
                    queue[:] = []
                    continue
                # 정지는 그 이후
                elif "10;0" in queue:
                    telloDrone.send_control_command("stop", 500)
                    queue[:] = []
                    continue

                # 일반 명령어 파싱
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
                        telloDrone.move_up(cmd[1])
                elif cmd[0] == 9:
                    if isFlying:
                        telloDrone.move_down(cmd[1])

    except Exception as e:
        errorDict['isError'] = True
        print('Error!', e)


# Flask 서버
def runFlask(queue, errorDict):
    app = Flask(__name__)

    # 이륙
    @app.route('/takeoff')
    def takeoff():
        if not errorDict['isError']:
            queue.append('0;0')

            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    # 착륙
    @app.route('/land')
    def land():
        if not errorDict['isError']:
            queue.append('1;0')

            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    # 전진
    @app.route('/forward')
    def moveForward():
        if not errorDict['isError']:
            distance = request.args.get('distance', 20)
            queue.append('2;{}'.format(int(distance)))

            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    # 후진
    @app.route('/back')
    def moveBack():
        if not errorDict['isError']:
            distance = request.args.get('distance', 20)
            queue.append('3;{}'.format(int(distance)))

            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    # 왼쪽
    @app.route('/left')
    def moveLeft():
        if not errorDict['isError']:
            distance = request.args.get('distance', 20)
            queue.append('4;{}'.format(int(distance)))

            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    # 오른쪽
    @app.route('/right')
    def moveRight():
        if not errorDict['isError']:
            distance = request.args.get('distance', 20)
            queue.append('5;{}'.format(int(distance)))

            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    # 회전 (CW)
    @app.route('/rotate_cw')
    def rotate_cw():
        if not errorDict['isError']:
            angle = request.args.get('angle', 30)
            queue.append('6;{}'.format(int(angle)))
            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    # 회전 (CCW)
    @app.route('/rotate_ccw')
    def rotate_ccw():
        if not errorDict['isError']:
            angle = request.args.get('angle', 30)
            queue.append('7;{}'.format(int(angle)))
            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    # 상승
    @app.route('/up')
    def up():
        if not errorDict['isError']:
            distance = request.args.get('distance', 30)
            queue.append('8;{}'.format(int(distance)))
            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )


    # 하강
    @app.route('/down')
    def down():
        if not errorDict['isError']:
            distance = request.args.get('distance', 30)
            queue.append('9;{}'.format(int(distance)))
            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    # 정지
    @app.route('/stop')
    def stop():
        if not errorDict['isError']:
            queue.append('10;0')
            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    # 비상 정지
    @app.route('/emergency')
    def emergency():
        if not errorDict['isError']:
            queue.append('11;0')
            return jsonify(
                code=200,
                success=True,
                msg='OK'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    app.run(host="0.0.0.0", port=8921)


if __name__ == '__main__':
    print('Tello Controller Server is Online!')

    # MultiProcessor간 변수 공유를 위한 Manager
    manager = multiprocessing.Manager()

    # 명령 Queue
    queueList = manager.list()

    # Error Check용 Dict
    errorDict = manager.dict({'isError': False})

    # 드론 Process
    telloProcess = Process(target=runTello, args=(queueList, errorDict))

    # Flask Process
    flaskProcess = Process(target=runFlask, args=(queueList, errorDict))

    flaskProcess.start()
    telloProcess.start()

    flaskProcess.join()
    telloProcess.join()
