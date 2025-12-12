package server;

import common.Question;
import common.ShuffleStrategy;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SmartShuffler implements ShuffleStrategy {
    private static final long serialVersionUID = 1L;

    @Override
    public void shuffle(List<Question> questions) {
        System.out.println("Executing SmartShuffler logic (Migrated Code)...");
        // Simple shuffle, but represents complex logic moving to client
        Collections.shuffle(questions, new Random(System.currentTimeMillis()));
    }
}
