import sys

from face_detector import FaceDetector
from face_recognizer import FaceRecognizer
import psycopg2

fd = FaceDetector()
fr = FaceRecognizer()

faces = fd.detect_file("test2.jpg")
users = []
for face in faces:
    user_id, confidence = fr.recognize(face, "f5e053ef-4f1d-4586-87e5-449864754549")
    try:
        conn = psycopg2.connect("dbname='InfiniteDrop' user='postgres' host='127.0.0.1' password='@Potinas235_pos'")
    except:
        print("unable to connect to the database")
        sys.exit(0)
    cur = conn.cursor()
    cur.execute("""SELECT name from face_user where id=%s""", [user_id])
    result = cur.fetchone()
    users.append(result[0])

print(users)
