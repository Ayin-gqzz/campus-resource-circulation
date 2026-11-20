package edu.campus.circulation.ui;

import edu.campus.circulation.service.DemoService.Exchange;
import edu.campus.circulation.service.DemoService.Status;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Maps visible group/child rows to the actual exchange; headings never represent a participant. */
final class ExchangeGroups {
    private ExchangeGroups() {}

    enum Side { BUY, SELL }

    static List<Exchange> forSide(List<Exchange> exchanges, long actorId, Side side) {
        return exchanges.stream().filter(exchange -> side == Side.BUY
                ? exchange.requesterId() == actorId : exchange.ownerId() == actorId).toList();
    }

    static List<Row> rows(List<Exchange> exchanges, Set<Long> expanded) {
        var groups = new LinkedHashMap<Long, List<Exchange>>();
        for (Exchange exchange : exchanges) {
            groups.computeIfAbsent(exchange.itemId(), ignored -> new ArrayList<>()).add(exchange);
        }
        List<Row> result = new ArrayList<>();
        for (var group : groups.entrySet()) {
            var values = group.getValue();
            int pending = (int) values.stream().filter(value -> value.status() == Status.PENDING).count();
            Exchange representative = values.stream().filter(value -> value.status() == Status.ACCEPTED)
                    .findFirst().orElse(values.get(0));
            result.add(new Row(group.getKey(), representative.itemTitle(), values.size(), pending, null));
            if (expanded.contains(group.getKey())) {
                for (Exchange exchange : values) {
                    result.add(new Row(group.getKey(), exchange.itemTitle(), values.size(), pending, exchange));
                }
            }
        }
        return List.copyOf(result);
    }

    record Row(long itemId, String title, int count, int pending, Exchange exchange) {
        boolean heading() { return exchange == null; }
    }
}
