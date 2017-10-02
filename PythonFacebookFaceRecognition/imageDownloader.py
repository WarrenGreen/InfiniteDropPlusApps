import requests as r
import sys
import psycopg2

GRAPH_API_URL = "https://graph.facebook.com/v2.7/"


def download_images(access_token, output_filename):
    params = {"access_token": access_token, "fields": "images"}

    try:
        conn = psycopg2.connect("dbname='InfiniteDrop' user='postgres' host='127.0.0.1' password='@Potinas235_pos'")
    except:
        print("unable to connect to the database")
        return
    cur = conn.cursor()
    cur.execute("""select photo_id from tagged_photos""")
    for row in cur.fetchall():
        photoId = row[0]
        resp = r.get(GRAPH_API_URL + photoId, params=params)
        images = resp.json()["images"]
        imgUrl = ""
        for img in images:
            if img["width"] == 720:
                imgUrl = str(img["source"])

        if imgUrl:
            imgUrlSplit = imgUrl.split("?")
            imgParams = {}
            if len(imgUrlSplit) > 1:
                imgUrl = imgUrlSplit[0]
                for param in imgUrlSplit[1].split("&"):
                    p = param.split("=")
                    key = p[0]
                    value = p[1]
                    imgParams[key] = value
            imageResp = r.get(imgUrl, params=imgParams);
            with open(output_filename + photoId + ".jpg", "wb") as imageFile:
                imageResp.raw.decode_content = True
                imageFile.write(imageResp.content)



if __name__ == '__main__':
    if len(sys.argv) < 3 or sys.argv[1] == "help":
        print("usage: imageDownloader.py <access token file> <output file>\n")
        print("outputs to index.csv\n")
        sys.exit(0)

    accessTokenFile = open(sys.argv[1], "r")
    access_token = accessTokenFile.readline().strip()
    accessTokenFile.close()

    output_filename = sys.argv[2].strip()
    download_images(access_token, output_filename)
