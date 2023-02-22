import cv2
import numpy as np

# gray8_image = cv2.imread("download-grey.png", cv2.IMREAD_ANYDEPTH)
gray8_image = cv2.imread("download.jpeg", cv2.IMREAD_ANYDEPTH)

# load the haar cascade face detector
haar_cascade_face = cv2.CascadeClassifier('haarcascade_frontalface_alt2.xml')
# detect faces in the input image using the haar cascade face detector
faces = haar_cascade_face.detectMultiScale(gray8_image, scaleFactor=1.1, minNeighbors=5, minSize=(10, 10))

# fever temperature threshold in Celsius or Fahrenheit
fever_temperature_threshold = 38.0
# fever_temperature_threshold = 99.0

# loop over the bounding boxes to measure their temperature
for (x, y, w, h) in faces:
    # draw the rectangles
    cv2.rectangle(gray8_image, (x, y), (x + w, y + h), (255, 255, 255), 1)

    # define the roi with a circle at the haar cascade origin coordinate
    # haar cascade center for the circle
    haar_cascade_circle_origin = x + w // 2, y + h // 4

    # circle radius
    radius = 2

    # The highest human temperature ever recorded is ~34 Celsius degree
    TEMP_RANGE = 255 / 44
    # print(gray8_image[x, y])
    # print(gray8_image[x, y] / TEMP_RANGE)
    # print(gray8_image[haar_cascade_circle_origin])
    # print(gray8_image[haar_cascade_circle_origin] / TEMP_RANGE)

    # calculate the temperature
    higher_temperature = gray8_image[haar_cascade_circle_origin] / TEMP_RANGE

    if higher_temperature < fever_temperature_threshold:
        # white text: normal temperature
        cv2.putText(gray8_image, "{0:.1f} Celsius".format(higher_temperature), (x - 10, y - 10),
                    cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 1)
        cv2.circle(gray8_image, haar_cascade_circle_origin, radius, (255, 255, 255), 2)
    else:
        # red text + red circle: fever temperature
        cv2.putText(gray8_image, "{0:.1f} Celsius".format(higher_temperature), (x - 10, y - 10),
                    cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2)
        cv2.circle(gray8_image, haar_cascade_circle_origin, radius, (0, 0, 255), 2)

# color the gray8 image using OpenCV colormaps
gray8_image = cv2.applyColorMap(gray8_image, cv2.COLORMAP_INFERNO)

# show result
cv2.imshow("face-detected", gray8_image)
cv2.waitKey(0)
