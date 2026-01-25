importScripts("https://www.gstatic.com/firebasejs/8.10.0/firebase-app.js");
importScripts("https://www.gstatic.com/firebasejs/8.10.0/firebase-messaging.js");

firebase.initializeApp({
    apiKey: "AIzaSyBTh8Ln5HrwTwkcyhtH0iKPa3JLQfOTetM",
    authDomain: "paychatkoli.firebaseapp.com",
    projectId: "paychatkoli",
    storageBucket: "paychatkoli.firebasestorage.app",
    messagingSenderId: "421426117703",
    appId: "1:421426117703:web:47d56f1a8e1bc3a8bc0562"
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
    console.log('[firebase-messaging-sw.js] Received background message ', payload);
    const notificationTitle = payload.notification.title;
    const notificationOptions = {
        body: payload.notification.body,
        icon: '/icons/Icon-192.png'
    };

    self.registration.showNotification(notificationTitle, notificationOptions);
});
