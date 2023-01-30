import cv2
import numpy as np

thermal_image = cv2.imread("download.jpeg", cv2.IMREAD_UNCHANGED)

# load the haar cascade face detector
haar_cascade_face = cv2.CascadeClassifier('haarcascade_frontalface_alt2.xml')
# detect faces in the input image using the haar cascade face detector
faces = haar_cascade_face.detectMultiScale(thermal_image, scaleFactor=1.1, minNeighbors=5, minSize=(10, 10))

# fever temperature threshold in Celsius or Fahrenheit
fever_temperature_threshold = 37.0
# fever_temperature_threshold = 99.0

# loop over the bounding boxes to measure their temperature
for (x, y, w, h) in faces:
    # draw the rectangles
    cv2.rectangle(thermal_image, (x, y), (x + w, y + h), (255, 255, 255), 1)

    # define the roi with a circle at the haar cascade origin coordinate
    # haar cascade center for the circle
    haar_cascade_circle_origin = x + w // 2, y + h // 2

    # circle radius
    radius = w // 4

    gray16_high_byte = (np.right_shift(thermal_image, 8)).astype('uint8')
    gray16_low_byte = ((np.left_shift(thermal_image, 8)) / 256).astype('uint16')

    # apply the mask to our 8 most significant bits
    mask = np.zeros_like(gray16_high_byte)
    cv2.circle(mask, haar_cascade_circle_origin, radius, (255, 255, 255), -1)
    gray16_high_byte = np.bitwise_and(gray16_high_byte, mask)

    # apply the mask to our 8 less significant bits
    mask = np.zeros_like(gray16_low_byte)
    cv2.circle(mask, haar_cascade_circle_origin, radius, (255, 255, 255), -1)
    gray16_low_byte = np.bitwise_and(gray16_low_byte, mask)
    # create / recompose our gray16 roi
    gray16_roi = np.array(gray16_high_byte, dtype=np.uint16)
    gray16_roi = gray16_roi * 256
    gray16_roi = gray16_roi | gray16_low_byte

    # estimate the face temperature by obtaining the higher value
    higher_temperature = np.amax(gray16_roi)
    # calculate the temperature
    higher_temperature = (higher_temperature / 100) - 273.15
    # higher_temperature = (higher_temperature / 100) * 9 / 5 - 459.67

    if higher_temperature < fever_temperature_threshold:
        # white text: normal temperature
        cv2.putText(thermal_image, "{0:.1f} Celsius".format(higher_temperature), (x - 10, y - 10),
                    cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 1)
    else:
        # red text + red circle: fever temperature
        cv2.putText(thermal_image, "{0:.1f} Celsius".format(higher_temperature), (x - 10, y - 10),
                    cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2)
        cv2.circle(thermal_image, haar_cascade_circle_origin, radius, (0, 0, 255), 2)

# show result
cv2.imshow("face-detected", thermal_image)
cv2.waitKey(0)
