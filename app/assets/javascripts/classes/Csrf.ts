import * as jwt_decode from "jwt-decode";

export default class Csrf {
    public static getToken() {
        let allCookies = document.cookie;
        let sessionCookie = allCookies.split("; ").find(value => value.split("=")[0] == "PLAY_SESSION").split("=")[1];
        let decoded = jwt_decode(sessionCookie);
        return decoded["data"]["csrfToken"];
    }
}
