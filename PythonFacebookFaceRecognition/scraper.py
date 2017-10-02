import requests as r
import sys
import psycopg2

GRAPH_API_URL="https://graph.facebook.com/v2.10/"


def scrape_images(access_token):
    try:
        conn = psycopg2.connect("dbname='InfiniteDrop' user='postgres' host='127.0.0.1' password='@Potinas235_pos'")
    except:
        print("unable to connect to the database")
    cur = conn.cursor()
    # Get Users ID
    params = {"access_token": access_token}
    resp = r.get(GRAPH_API_URL + "me", params=params)
    print(resp.json())
    user_id = resp.json()["id"]

    run = True
    after = None
    outFile = open("index.csv", "w")
    pages = 0
    while run and pages < 20:  # hacked do while
        params = {"access_token": access_token, "after": after}
        resp = r.get(GRAPH_API_URL + user_id + "/photos", params=params)
        data = resp.json()["data"]

        if "paging" in resp.json():
            after = resp.json()["paging"]["cursors"]["after"]
        else:
            run = False

        pages += 1
        for photo in data:
            photoId = photo["id"]
            tagsParams = {"access_token": access_token}
            tagsResp = r.get(GRAPH_API_URL + photoId + "/tags", params=tagsParams)
            tagsData = tagsResp.json()["data"]
            for tag in tagsData:
                x = tag["x"]
                y = tag["y"]
                print(str(photoId) + "," + str(x) + "," + str(y) + ", " + tag["name"])
                outFile.write("{0}, {1}, {2}, {3}\n".format(photoId, x, y, tag["name"]))
                cur.execute("""INSERT INTO face_user (name, fb_access_token, fb_id) VALUES (%s, %s, %s) ON CONFLICT DO NOTHING""", (tag["name"], None, tag["id"]))
                cur.execute("""INSERT INTO tagged_photos (user_id, photo_id, x, y) VALUES ((select id from face_user where fb_id=%s), %s, %s, %s) ON CONFLICT DO NOTHING""", (tag["id"],photoId, x, y ))
                cur.execute("""INSERT INTO user_relationship (user1, user2) VALUES ((select id from face_user where fb_id=%s), (select id from face_user where fb_id=%s)) ON CONFLICT DO NOTHING""", (user_id, tag["id"]))
                cur.execute("""INSERT INTO user_relationship (user1, user2) VALUES ((select id from face_user where fb_id=%s), (select id from face_user where fb_id=%s)) ON CONFLICT DO NOTHING""", (tag["id"], user_id))
    conn.commit()
    cur.close()
    conn.close()

if __name__ == '__main__':
    if len(sys.argv) < 2 or sys.argv[1] == "help":
        print("usage: scraper.py <access token file>\n")
        print("outputs to index.csv\n")
        sys.exit(0)

    accessTokenFile = open(sys.argv[1], "r")
    access_token = accessTokenFile.readline().strip()
    scrape_images(access_token)
