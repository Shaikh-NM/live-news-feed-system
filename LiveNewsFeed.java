import java.util.*;

public class LiveNewsFeed {

    private static final class Article {
        final String id;
        final String title;
        final int time;

        Article(String id, String title, int time) {
            this.id = id;
            this.title = title;
            this.time = time;
        }

        String format() {
            return id + " | " + title + " | " + time;
        }
    }

    private static final class User {
        // topicId -> subscription intervals, expressed in "article index" space:
        // [start, end) means the user was subscribed while articles with global
        // index in that range were published. The open interval has end = MAX_VALUE.
        final Map<String, List<int[]>> intervals = new HashMap<>();
        // Articles with index < lastNotifiedIndex were already handed out.
        int lastNotifiedIndex = 0;

        boolean isSubscribed(String topic) {
            List<int[]> list = intervals.get(topic);
            return list != null && !list.isEmpty()
                    && list.get(list.size() - 1)[1] == Integer.MAX_VALUE;
        }
    }

    private final Map<String, User> users = new HashMap<>();
    private final Set<String> topics = new HashSet<>();

    // All articles in publish order; position in this list is the global index.
    private final List<Article> articles = new ArrayList<>();
    private final Set<String> articleIds = new HashSet<>();
    // topicId -> ascending list of global article indices published under that topic.
    private final Map<String, List<Integer>> topicArticles = new HashMap<>();

    public LiveNewsFeed(List<String> existingUserIds, List<String> existingTopicIds) {
        for (String u : existingUserIds) users.put(u, new User());
        for (String t : existingTopicIds) {
            topics.add(t);
            topicArticles.put(t, new ArrayList<>());
        }
    }

    public synchronized boolean subscribeUnsubscribeTopic(String userId, String topicId,
                                                          boolean subscribe, int currentTime) {
        User user = users.get(userId);
        if (user == null || !topics.contains(topicId)) return false;

        boolean already = user.isSubscribed(topicId);
        if (already == subscribe) return false;

        int boundary = articles.size(); // calls are sequential, so this is exact
        if (subscribe) {
            user.intervals.computeIfAbsent(topicId, k -> new ArrayList<>())
                    .add(new int[]{boundary, Integer.MAX_VALUE});
        } else {
            List<int[]> list = user.intervals.get(topicId);
            list.get(list.size() - 1)[1] = boundary;
        }
        return true;
    }

    public synchronized boolean publishArticle(String articleId, String title,
                                               List<String> articleTopics, int currentTime) {
        if (articleId == null || articleId.isEmpty() || title == null
                || articleTopics == null || articleTopics.isEmpty()
                || currentTime < 0 || articleIds.contains(articleId)) {
            return false;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String t : articleTopics) {
            if (t == null || !topics.contains(t)) return false;
            distinct.add(t);
        }

        int index = articles.size();
        articles.add(new Article(articleId, title, currentTime));
        articleIds.add(articleId);
        for (String t : distinct) topicArticles.get(t).add(index);
        return true;
    }

    public synchronized List<String> getPersonalizedFeed(String userId) {
        User user = users.get(userId);
        if (user == null) return new ArrayList<>();

        // Dedup across topics via a set of global indices.
        Set<Integer> matched = new HashSet<>();
        for (Map.Entry<String, List<int[]>> e : user.intervals.entrySet()) {
            String topic = e.getKey();
            if (!user.isSubscribed(topic)) continue; // must still be subscribed now
            collect(topicArticles.get(topic), e.getValue(), 0, matched);
        }

        List<Article> result = new ArrayList<>(matched.size());
        for (int idx : matched) result.add(articles.get(idx));
        result.sort((a, b) -> a.time != b.time
                ? Integer.compare(b.time, a.time)
                : a.id.compareTo(b.id));

        List<String> out = new ArrayList<>(result.size());
        for (Article a : result) out.add(a.format());
        return out;
    }

    public synchronized List<String> getNotifications(String userId) {
        User user = users.get(userId);
        if (user == null) return new ArrayList<>();

        // Every interval (open or closed) counts: the user was subscribed when
        // those articles were published, so they stay deliverable after unsubscribe.
        TreeSet<Integer> pending = new TreeSet<>(); // sorted = publish order, deduped
        for (Map.Entry<String, List<int[]>> e : user.intervals.entrySet()) {
            collect(topicArticles.get(e.getKey()), e.getValue(), user.lastNotifiedIndex, pending);
        }
        user.lastNotifiedIndex = articles.size();

        List<String> out = new ArrayList<>(pending.size());
        for (int idx : pending) out.add(articles.get(idx).format());
        return out;
    }

    /** Adds to 'sink' every topic article whose index is >= minIndex and inside any interval. */
    private void collect(List<Integer> topicList, List<int[]> ivs, int minIndex, Collection<Integer> sink) {
        for (int[] iv : ivs) {
            int from = Math.max(iv[0], minIndex);
            if (from >= iv[1]) continue;
            for (int i = lowerBound(topicList, from); i < topicList.size(); i++) {
                int idx = topicList.get(i);
                if (idx >= iv[1]) break;
                sink.add(idx);
            }
        }
    }

    private static int lowerBound(List<Integer> list, int target) {
        int lo = 0, hi = list.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (list.get(mid) < target) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    // ---------- quick demo (Examples 2 and 3) ----------
    public static void main(String[] args) {
        LiveNewsFeed f = new LiveNewsFeed(List.of("u1"), List.of("finance"));
        f.subscribeUnsubscribeTopic("u1", "finance", true, 50);
        f.publishArticle("a1", "Market Opens Higher", List.of("finance"), 200);
        f.subscribeUnsubscribeTopic("u1", "finance", false, 220);
        f.publishArticle("a2", "Closing Bell", List.of("finance"), 250);
        System.out.println(f.getNotifications("u1"));    // [a1 | Market Opens Higher | 200]
        System.out.println(f.getPersonalizedFeed("u1")); // []

        LiveNewsFeed g = new LiveNewsFeed(List.of("u1"), List.of("tech"));
        g.subscribeUnsubscribeTopic("u1", "tech", true, 250);
        g.publishArticle("a2", "AI Update", List.of("tech"), 300);
        g.publishArticle("a1", "Chip Update", List.of("tech"), 300);
        System.out.println(g.getPersonalizedFeed("u1")); // [a1 | Chip Update | 300, a2 | AI Update | 300]
        System.out.println(g.getNotifications("u1"));    // [a2 | AI Update | 300, a1 | Chip Update | 300]
    }
}