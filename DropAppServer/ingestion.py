import sys

import time

import cv2

import dropbox
import psycopg2
from face_detector import FaceDetector
from face_recognizer import FaceRecognizer

while (True):
    fr = FaceRecognizer()
    fd = FaceDetector()

    try:
        conn = psycopg2.connect("dbname='InfiniteDrop' user='postgres' host='127.0.0.1' password='@Potinas235_pos'")
    except:
        print("unable to connect to the database")
        sys.exit(0)
    cur = conn.cursor()
    cur.execute("""SELECT file.id as file_id, file.infinite_drop_user_id as user_id, filename, hash, dbx_user_id, dbx_access_token, infinite_drop_user.face_id  FROM file, dbx_account, infinite_drop_user WHERE infinite_drop_user.id=file.infinite_drop_user_id AND file.dbx_account_id=dbx_account.id and file.id not in (SELECT file_id from processed_photos) and filename like '%%jpg'""")
    rows = cur.fetchall()

    for row in rows:
        ifile_id = row[0]
        idrop_user_id = row[1]
        filename = row[2]
        ext = filename[-4:]
        hash = row[3]
        dbx_user_id = row[4]
        dbx_access_token = row[5]
        face_id = row[6]
        dbx = dropbox.Dropbox(dbx_access_token)
        dbx.files_download_to_file(hash+ext, "/"+hash)
        img = cv2.imread(hash+ext)
        print(img.shape)
        faces = fd.detect(img)
        #faces = fd.detect_file(hash+ext)
        for face in faces:
            predicted_user_id, confidence = fr.recognize(face, face_id)
            print(predicted_user_id)
            if predicted_user_id:
                print("INSERTED")
                cur.execute("""INSERT INTO processed_photos (file_id, user_id) VALUES (%s, %s)""" , [ifile_id, predicted_user_id])

    conn.commit()
    time.sleep(.5)





