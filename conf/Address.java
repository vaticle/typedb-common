package com.vaticle.typedb.common.conf;

import java.net.InetSocketAddress;

import static java.lang.Integer.parseInt;

public class Address {

    private final InetSocketAddress external;
    private final InetSocketAddress internalZMQ;
    private final InetSocketAddress internalGRPC;

    public static Address create(String external, String internalZMQ, String internalGRPC) {
        String[] ext = external.split(":");
        String[] intZMQ = internalZMQ.split(":");
        String[] intGRPC = internalGRPC.split(":");
        return new Address(ext[0], parseInt(ext[1]), intZMQ[0], parseInt(intZMQ[1]), intGRPC[0], parseInt(intGRPC[1]));
    }

    public Address(
            String externalHost,
            int externalPort,
            String internalHostZMQ,
            int internalPortZMQ,
            String internalHostGRPC,
            int internalPortGRPC
    ) {
        this(
                new InetSocketAddress(externalHost, externalPort),
                new InetSocketAddress(internalHostZMQ, internalPortZMQ),
                new InetSocketAddress(internalHostGRPC, internalPortGRPC)
        );
    }

    public Address(InetSocketAddress external, InetSocketAddress internalZMQ, InetSocketAddress internalGRPC) {
        this.external = external;
        this.internalZMQ = internalZMQ;
        this.internalGRPC = internalGRPC;
    }

    public String external() {
        return external.getHostString() + ":" + external.getPort();
    }

    public InetSocketAddress external2() {
        return external;
    }

    public String internalZMQ() {
        return internalZMQ.getHostString() + ":" + internalZMQ.getPort();
    }

    public InetSocketAddress internalZMQ2() {
        return internalZMQ;
    }

    public String internalGRPC() {
        return internalGRPC.getHostString() + ":" + internalGRPC.getPort();
    }

    public InetSocketAddress internalGRPC2() {
        return internalGRPC;
    }
}
