package com.example.user.registration;

public abstract class MemberAbstractClass {
    public void createMember() {
        // -> calls create principal
        // stub.callauth()->
        registerMember();
        verifyMember();
    }

    public void registerMember() {
        // register
    }

    abstract void verifyMember();

}
