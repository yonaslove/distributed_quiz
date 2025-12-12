package common;

import java.io.Serializable;
import java.util.List;

public interface ShuffleStrategy extends Serializable {
    void shuffle(List<Question> questions);
}
