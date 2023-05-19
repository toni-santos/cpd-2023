package server;

import java.nio.ByteBuffer;

public enum ServerCodes {
    // Confirmation codes
    OK,
    ERR,

    // Log in/Register codes
    LOG,
    REG,
    DC,
    ERRDC,

    // Reconnect codes
    REC, // reconnect
    Q, // in queue
    G, // in game


    // Gamemode codes
    N1, // Normal 1v1
    N2, // Normal 2v2
    R1, // Ranked 1v1
    R2, // Ranked 2v2

    // Game flow
    GF, // Gmme Found
    GG, // Game Over
    GS, // Game Start
    GP  // Game Port

}
