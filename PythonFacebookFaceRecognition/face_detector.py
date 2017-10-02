import os
from math import sqrt, pow

import cv2
import psycopg2


class FaceDetector:
    def distance(self, matchX, matchY, faceX, faceY):
        matchX = float(matchX)
        matchY = float(matchY)
        faceX = float(faceX)
        faceY = float(faceY)
        return sqrt(pow(faceX - matchX,2) + pow(faceY - matchY, 2));

    def ingest(self, input_directory, output_directory):
        face_cascade = cv2.CascadeClassifier('haarcascade_frontalface_default.xml')
        try:
            conn = psycopg2.connect("dbname='InfiniteDrop' user='postgres' host='127.0.0.1' password='@Potinas235_pos'")
        except:
            print("unable to connect to the database")
            return
        cur = conn.cursor()
        cur.execute("""select photo_id, user_id, x, y from tagged_photos""")
        valid_photo_ids = {}
        for row in cur.fetchall():
            if row[0] not in valid_photo_ids:
                valid_photo_ids[row[0]] = []
            valid_photo_ids[row[0]].append((row[1], row[2], row[3]))

        cropped_faces = {}
        for filename in os.listdir(input_directory):
            if filename.endswith('.jpg'):
                photo_id = filename[:-4]
                if photo_id not in valid_photo_ids:
                    continue

                img = cv2.imread(os.path.join(input_directory, filename))
                if img is None or not img.size:
                    continue
                gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
                gray = cv2.equalizeHist(gray)
                faces = face_cascade.detectMultiScale(gray)
                for tag in valid_photo_ids[photo_id]:
                    matched_face = None
                    actual_x = float(tag[1])
                    actual_x = int(actual_x * img.shape[1]) / 100
                    actual_y = float(tag[2])
                    actual_y = int(actual_y * img.shape[0]) / 100
                    user_id = tag[0]
                    for face in faces:
                        if (matched_face is None or (matched_face[0] == 0 and matched_face[1] == 0)
                                or self.distance(matched_face[0], matched_face[1], actual_x, actual_y) > self.distance(face[0], face[1], actual_x,actual_y)):
                            matched_face = face

                    if user_id not in cropped_faces:
                        cropped_faces[user_id] = []

                    if matched_face is not None:
                        x, y, w, h = matched_face
                        finalFace = gray[y:y+h, x:x+w]
                        cv2.rectangle(img, (x, y), (x + w, y + h), (255, 0, 0), 2)
                        cropped_faces[user_id].append((finalFace, photo_id))

        for user_id in cropped_faces:
            user_directory = os.path.join(output_directory, user_id)
            if not os.path.exists(user_directory):
                os.makedirs(user_directory)
            for face, photo_id in cropped_faces[user_id]:
                output_file = str(os.path.join(os.getcwd(), user_directory, photo_id+'.jpg'))
                print(output_file)
                print(cv2.imwrite(output_file, face))

        conn.commit()
        cur.close()
        conn.close()

    def detect_file(self, filename):
        img = cv2.imread(filename)
        return self.detect(img)

    def detect(self, img):
        face_cascade = cv2.CascadeClassifier('/Users/wsgreen/workspace/UpdatedDrop/PythonFacebookFaceRecognition/haarcascade_frontalface_default.xml')
        if img is None or not img.size:
            print("Couldn't read image")
            return None
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        gray = cv2.equalizeHist(gray)
        print(gray.shape)
        faces = face_cascade.detectMultiScale(gray)
        cropped_faces = []
        for x,y,w,h in faces:
            cropped_faces.append(gray[y:y+h, x:x+w])

        return cropped_faces


if __name__ == '__main__':
    fd = FaceDetector()
    fd.ingest('images', 'faces')
