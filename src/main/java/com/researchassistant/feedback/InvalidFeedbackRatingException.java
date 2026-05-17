package com.researchassistant.feedback;

public class InvalidFeedbackRatingException extends RuntimeException {

    public InvalidFeedbackRatingException(String rating) {
        super("Feedback rating must be 'up' or 'down': " + rating);
    }
}
