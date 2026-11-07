package com.networkproxy.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

public class DataRelay {

    public void relay(Socket clientSocket, Socket remoteSocket)
            throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(2);

        Thread clientToRemote = new Thread(() -> {

            try {

                copyData(clientSocket, remoteSocket);

            } catch (IOException e) {

                System.err.println(
                        "Loi chuyen tiep Client -> Server: "
                                + e.getMessage()
                );

            } finally {

                shutdownOutput(remoteSocket);
                latch.countDown();
            }

        });

        Thread remoteToClient = new Thread(() -> {

            try {

                copyData(remoteSocket, clientSocket);

            } catch (IOException e) {

                System.err.println(
                        "Loi chuyen tiep Server -> Client: "
                                + e.getMessage()
                );

            } finally {

                shutdownOutput(clientSocket);
                latch.countDown();
            }

        });

        clientToRemote.start();
        remoteToClient.start();

        latch.await();
    }

    private void copyData(Socket source, Socket destination)
            throws IOException {

        InputStream input = source.getInputStream();

        OutputStream output = destination.getOutputStream();

        byte[] buffer = new byte[8192];

        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {

            output.write(buffer, 0, bytesRead);

            output.flush();
        }
    }

    private void shutdownOutput(Socket socket) {

        try {

            if (!socket.isClosed()) {
                socket.shutdownOutput();
            }

        } catch (IOException e) {

            System.err.println(
                    "Loi shutdownOutput: " + e.getMessage()
            );
        }
    }
}