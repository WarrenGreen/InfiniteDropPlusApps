import base64

import dropbox
import psycopg2

from flask import Flask, render_template
from flask import request
app = Flask(__name__)


def is_image(filename):
    image_extensions = ['jpg', 'png',' jpeg']
    for ext in image_extensions:
        if filename.endswith(ext):
            return True

    return False

@app.route('/index',methods=['GET'])
def index():
    user_id= request.args.get('user_id')
    try:
        conn = psycopg2.connect("dbname='InfiniteDrop' user='postgres' host='127.0.0.1' password='@Potinas235_pos'")
    except:
        print("unable to connect to the database")
        return None, None
    cur = conn.cursor()
    cur.execute("""select * from file where infinite_drop_user_id=%s""", [user_id])
    rows = cur.fetchall()
    files = []
    for row in rows:
        file_id = row[0]
        filename = row[3]
        files.append([file_id, filename, is_image(filename)])

    return render_template('index.html', files=files)

@app.route('/image',methods=['GET'])
def image():
    user_id = request.args.get('user_id')
    file_id = request.args.get('file_id')
    try:
        conn = psycopg2.connect("dbname='InfiniteDrop' user='postgres' host='127.0.0.1' password='@Potinas235_pos'")
    except:
        print("unable to connect to the database")
        return None, None
    people = []
    cur = conn.cursor()
    cur.execute("""SELECT name, hash, dbx_access_token FROM processed_photos, face_user, file, dbx_account WHERE dbx_account_id=dbx_account.id AND file_id=file.id AND file_id=%s AND processed_photos.user_id=face_user.id""", [file_id])
    rows = cur.fetchall()
    try:
        hash = rows[0][1]
        dbx_access_token = rows[0][2]
    except:
        pass
    for row in rows:
        people.append(row[0])

    dbx = dropbox.Dropbox(dbx_access_token)
    _, response = dbx.files_download("/"+hash)
    img = response.content
    encoded = "data:image/jpg;base64, {}".format(str(base64.b64encode(img))[2:-1])
    return render_template('image.html', people=people, b64img=encoded)
