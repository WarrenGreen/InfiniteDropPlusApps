import cv2
import os
import psycopg2
import numpy as np
import pickle


class FaceRecognizer:

    def train(self, training_data_directory):
        models = {}
        faces = {}
        dataset = {}

        try:
            conn = psycopg2.connect("dbname='InfiniteDrop' user='postgres' host='127.0.0.1' password='@Potinas235_pos'")
        except:
            print("unable to connect to the database")
            return
        cur = conn.cursor()
        num_faces = 0

        for filename in os.listdir(training_data_directory):
            if filename not in faces:
                faces[filename] = []

            for face_filename in os.listdir(os.path.join(training_data_directory, filename)):
                img = cv2.imread(os.path.join(training_data_directory, filename, face_filename), 0)
                faces[filename].append(img)
                num_faces += 1

        for user_id in faces:
            models[user_id] = cv2.face.LBPHFaceRecognizer_create()
            dataset[user_id] = {'data': [], 'labels': np.zeros(num_faces, dtype=np.int32)}
            labels_index = 0
            for id in faces:
                for face_img in faces[id]:
                    dataset[user_id]['data'].append(face_img)
                    if id == user_id:
                        dataset[user_id]['labels'][labels_index] = 1
                    labels_index+=1

            #print(dataset[user_id])
            models[user_id].train(dataset[user_id]['data'], dataset[user_id]['labels'])
            #pickle.dump(models[user_id], open(os.path.join("models", user_id), 'wb'))
            #model_state = pickle.dumps(models[user_id])
            models[user_id].write(os.path.join("models", user_id))
            model_state_file = open(os.path.join("models", user_id), 'r')
            model_state = model_state_file.read()

            cur.execute("""INSERT INTO face_models (user_id, model_state) VALUES (%s , %s)""", (user_id, model_state))

        conn.commit()
        cur.close()
        conn.close()

    def recognize_file(self, filename, user_id):
        return self.recognize(cv2.imread(filename, 0), user_id)

    def recognize(self, img, user_id):
        try:
            conn = psycopg2.connect("dbname='InfiniteDrop' user='postgres' host='127.0.0.1' password='@Potinas235_pos'")
        except:
            print("unable to connect to the database")
            return None, None
        cur = conn.cursor()
        cur.execute("""SELECT user2 as friend from user_relationship where user1=%s::uuid""", [user_id])
        results = cur.fetchall()
        users = [user_id]
        for result in results:
            users.append(result[0])

        max_confidence = -1.0
        user_prediction = 0
        for user in users:
            modelpath = os.path.join("/Users/wsgreen/workspace/UpdatedDrop/PythonFacebookFaceRecognition/models", user)
            if not os.path.isfile(modelpath):
                print("nofile", modelpath)
                continue
            model = cv2.face.LBPHFaceRecognizer_create()
            model.read(modelpath)
            model.setThreshold(90.0)
            prediction, confidence = model.predict(img)
            print(prediction, confidence)
            if prediction and confidence > max_confidence:
                user_prediction = user
                max_confidence = confidence

        return user_prediction, max_confidence



if __name__ == '__main__':
    fr = FaceRecognizer()
    #fr.train("faces")
    user, confidence = fr.recognize_file("/Users/wsgreen/workspace/UpdatedDrop/PythonFacebookFaceRecognition/faces/f5e053ef-4f1d-4586-87e5-449864754549/498848063618208.jpg", "f5e053ef-4f1d-4586-87e5-449864754549")
    print(user, confidence)