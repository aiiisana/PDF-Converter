package com.example.pdfconverter.service;

import com.example.pdfconverter.model.SubscriptionType;
import com.example.pdfconverter.model.User;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private Firestore getDb() {
        return FirestoreClient.getFirestore();
    }

    public User loadOrCreateUser(String uid, String email) {
        DocumentReference ref = getDb().collection("users").document(uid);
        try {
            DocumentSnapshot doc = ref.get().get();
            if (doc.exists()) return doc.toObject(User.class);
            User user = new User(uid, email, SubscriptionType.FREE);
            ref.set(user);
            return user;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load or create user", e);
        }
    }
}
