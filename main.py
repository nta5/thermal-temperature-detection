import io
from PIL import Image
import cv2
import socket


def get_temp(filename):
    gray8_image = cv2.imread(filename, cv2.IMREAD_ANYDEPTH)

    # load the haar cascade face detector
    haar_cascade_face = cv2.CascadeClassifier('haarcascade_frontalface_alt2.xml')
    # detect faces in the input image using the haar cascade face detector
    faces = haar_cascade_face.detectMultiScale(gray8_image, scaleFactor=1.1, minNeighbors=5, minSize=(10, 10))

    higher_temperature = "NOT DETECTED"
    # loop over the bounding boxes to measure their temperature
    for (x, y, w, h) in faces:
        # define the roi with a circle at the haar cascade origin coordinate
        # haar cascade center for the circle
        haar_cascade_circle_origin = x + w // 2, y + h // 4

        # The highest human temperature ever recorded is ~43 Celsius degree
        TEMP_RANGE = 255 / 43

        # calculate the temperature
        higher_temperature = gray8_image[haar_cascade_circle_origin] / TEMP_RANGE

    return higher_temperature


if __name__ == "__main__":
    server_sock = socket.socket(socket.AF_INET)
    print("Socket successfully created")

    # reserve a port on your computer
    port = 8800

    # Next bind to the port
    server_sock.bind(('', port))
    print("socket binded to %s" % port)

    # put the socket into listening mode
    server_sock.listen(5)
    print("socket is listening")

    # a forever loop until we interrupt it or
    # an error occurs
    while True:
        # Establish connection with client.
        client_sock, addr = server_sock.accept()
        print('Got connection from', addr)

        # data = client_sock.recv(1024)
        # length = data.decode()
        #
        # print("image length: " + length)

        # send a thankyou message to the client. encoding to send byte type.
        client_sock.send('Thank you for connecting'.encode())

        count = 0
        datalist = []
        while True:
            data = client_sock.recv(1024)
            datalist.append(data)

            count += len(data)

            # if no data left
            if len(data) <= 0:
                break

            # flag to detect the end of each frame
            flag = "\r\n\r\n"
            if data.endswith(str.encode(flag)):
                # img size
                # print("total length:" + str(count))

                # save img to the working directory
                byte_stream = io.BytesIO(b''.join(datalist))
                img = Image.open(byte_stream)
                img.save("image.jpeg")

                # get temperature from the image
                temp = get_temp("image.jpeg")

                # send temperature back to client
                print("Result: " + temp)
                try:
                    temp = float(temp)
                    client_sock.sendall(str(temp).encode())
                except ValueError as e:
                    client_sock.sendall(temp.encode())

                # reset img size and data list
                count = 0
                datalist = []

        # Close the connection with the client
        client_sock.close()

        # Breaking once connection closed
        break


# def test_local_file(filename):
#     gray8_image = cv2.imread(filename, cv2.IMREAD_ANYDEPTH)
#
#     # load the haar cascade face detector
#     haar_cascade_face = cv2.CascadeClassifier('haarcascade_frontalface_alt2.xml')
#     # detect faces in the input image using the haar cascade face detector
#     faces = haar_cascade_face.detectMultiScale(gray8_image, scaleFactor=1.1, minNeighbors=5, minSize=(10, 10))
#
#     # fever temperature threshold in Celsius
#     fever_temperature_threshold = 38.0
#
#     higher_temperature = "NOT DETECTED"
#     # loop over the bounding boxes to measure their temperature
#     for (x, y, w, h) in faces:
#         # draw the rectangles
#         cv2.rectangle(gray8_image, (x, y), (x + w, y + h), (255, 255, 255), 1)
#
#         # define the roi with a circle at the haar cascade origin coordinate
#         # haar cascade center for the circle
#         haar_cascade_circle_origin = x + w // 2, y + h // 4
#
#         # circle radius
#         radius = 2
#
#         # The highest human temperature ever recorded is ~43 Celsius degree
#         TEMP_RANGE = 255 / 43
#         print(gray8_image[x, y])
#         print(gray8_image[x, y] / TEMP_RANGE)
#         print(gray8_image[haar_cascade_circle_origin])
#         print(gray8_image[haar_cascade_circle_origin] / TEMP_RANGE)
#
#         # calculate the temperature
#         higher_temperature = gray8_image[haar_cascade_circle_origin] / TEMP_RANGE
#
#         if higher_temperature < fever_temperature_threshold:
#             # white text: normal temperature
#             cv2.putText(gray8_image, "{0:.1f} Celsius".format(higher_temperature), (x - 10, y - 10),
#                         cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 1)
#             cv2.circle(gray8_image, haar_cascade_circle_origin, radius, (255, 255, 255), 2)
#         else:
#             # red text + red circle: fever temperature
#             cv2.putText(gray8_image, "{0:.1f} Celsius".format(higher_temperature), (x - 10, y - 10),
#                         cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2)
#             cv2.circle(gray8_image, haar_cascade_circle_origin, radius, (0, 0, 255), 2)
#
#     # color the gray8 image using OpenCV colormaps
#     gray8_image = cv2.applyColorMap(gray8_image, cv2.COLORMAP_INFERNO)
#
#     # show result
#     cv2.imshow("face-detected", gray8_image)
#     cv2.waitKey(0)
#     return higher_temperature
