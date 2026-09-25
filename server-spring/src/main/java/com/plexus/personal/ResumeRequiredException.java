package com.plexus.personal;

public class ResumeRequiredException extends RuntimeException {
    public ResumeRequiredException() {
        super("请先完善简历档案");
    }
}
