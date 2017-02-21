package pw.phylame.crawling.task;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public class TaskProgressEvent {
    private final int total;
    private final int progress;
}
