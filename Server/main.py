import multiprocessing
import os.path
from djitellopy import Tello
from flask import Flask, request, jsonify, send_file
from multiprocessing import Process
import cv2
import numpy as np
face_cascade = cv2.CascadeClassifier('xml/haarcascade_frontalface_default.xml')

# Canny Edge Detection
def cannyEdge(image):
    return cv2.Canny(image, 80, 200)

# Face Detection using Face Cascade
def detectFace(image):
    cpyImage = image.copy()
    frame_gray = cv2.cvtColor(cpyImage, cv2.COLOR_BGR2GRAY)
    face_detection = face_cascade.detectMultiScale(frame_gray)

    for x, y, w, h in face_detection:
        cpyImage = cv2.rectangle(cpyImage, (x, y), (x + w, y + h), (0, 255, 0), 3)
    return cpyImage


def runTello(queue, errorDict):
    try:
        telloDrone = Tello(host="192.168.10.1")
        telloDrone.connect()

        telloDrone.TAKEOFF_TIMEOUT = 10
        telloDrone.RESPONSE_TIMEOUT = 3
        telloDrone.RETRY_COUNT = 2
        telloDrone.for_back_velocity = 0
        telloDrone.left_right_velocity = 0
        telloDrone.up_down_velocity = 0
        telloDrone.yaw_velocity = 0
        telloDrone.speed = 0

        print('Drone Battery Percentage : ' + str(telloDrone.get_battery()) + '%')

        telloDrone.streamoff()

        isFlying = False
        isStreamOn = False
        while True:
            if queue:
                # 비상 정지 최우선
                if "12;0" in queue:
                    telloDrone.send_command_without_return("emergency")
                    queue[:] = []
                    continue
                # 정지는 그 다음 우선
                if "11;0" in queue:
                    telloDrone.send_command_without_return("stop")
                    queue[:] = []
                    continue

                # 일반 명령어 파싱
                cmd = list(map(int, queue.pop(0).split(';')))
                print(cmd)

                if cmd[0] == 0:
                    if not isFlying:
                        telloDrone.takeoff()
                        queue[:] = []
                        isFlying = True
                elif cmd[0] == 1:
                    if isFlying:
                        telloDrone.land()
                        queue[:] = []
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
                elif cmd[0] == 10:
                    if isFlying:
                        telloDrone.set_speed(cmd[1])
                elif cmd[0] == 13:
                    if not isStreamOn:
                        telloDrone.streamon()
                        isStreamOn = True
                elif cmd[0] == 14:
                    if isStreamOn:
                        telloDrone.streamoff()
                        isStreamOn = False
                elif cmd[0] == 15:
                    if isStreamOn:
                        print('capture!')
                        droneFrame = telloDrone.get_frame_read()
                        print('saved')
                        cv2.imwrite('./capture/image.png', droneFrame.frame)
                elif cmd[0] == 16:
                    if isFlying:
                        telloDrone.send_command_without_return("stop")

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

    # 속도
    @app.route('/speed')
    def speed():
        if not errorDict['isError']:
            speed = request.args.get('speed', 30)
            queue.append('10;{}'.format(int(speed)))
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
            force = request.args.get('force', 0)
            if force == 0:
                queue.append('16;0')
            else:
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

    # 비상 정지
    @app.route('/emergency')
    def emergency():
        if not errorDict['isError']:
            queue.append('12;0')
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

    # Stream On
    @app.route('/streamon')
    def streamon():
        if not errorDict['isError']:
            queue.append('13;0')
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

    # Stream Off
    @app.route('/streamoff')
    def streamoff():
        if not errorDict['isError']:
            queue.append('14;0')
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

    # Capture
    @app.route('/capture')
    def capture():
        if not errorDict['isError']:
            if os.path.exists('./capture/image.png'):
                os.remove('./capture/image.png')
            if os.path.exists('./capture/image_edit.png'):
                os.remove('./capture/image_edit.png')

            queue.append('15;0')

            return jsonify(
                code=200,
                success=True,
                msg='success'
            )
        else:
            return jsonify(
                code=500,
                success=False,
                msg='Drone Connection Error'
            )

    # Get Last Capture
    @app.route('/getcapture')
    def getcapture():
        if not errorDict['isError']:
            if os.path.exists('./capture/image.png'):
                originalImg = cv2.imread('./capture/image.png')

                print('processing..')

                imgSize = (originalImg.shape[1], originalImg.shape[0])

                edgeImg = cv2.cvtColor(cannyEdge(originalImg), cv2.COLOR_GRAY2BGR)
                faceImg = detectFace(originalImg)

                print(edgeImg.shape, faceImg.shape)

                originalResize = cv2.resize(originalImg, dsize=(imgSize[0] * 2, imgSize[1] * 2),
                                            interpolation=cv2.INTER_AREA)

                concatImage = cv2.hconcat([edgeImg, faceImg])
                concatImage = cv2.vconcat([concatImage, originalResize])

                cv2.imwrite('./capture/image_edit.png', concatImage)

                return send_file('./capture/image_edit.png', mimetype='image/png')
            else:
                return jsonify(
                    code=500,
                    success=False,
                    msg='Image File Not Found'
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
