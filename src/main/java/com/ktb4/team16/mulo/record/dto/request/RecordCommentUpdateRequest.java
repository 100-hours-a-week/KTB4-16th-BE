package com.ktb4.team16.mulo.record.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;

public class RecordCommentUpdateRequest {
    private String comment;
    private boolean commentProvided;

    @Size(max = 80, message = "COMMENT_TOO_LONG")
    public String getComment() {
        return comment;
    }

    @JsonSetter("comment")
    public void setComment(String comment) {
        this.comment = comment;
        this.commentProvided = true;
    }

    @JsonIgnore
    public boolean hasCommentField() {
        return commentProvided;
    }
}
