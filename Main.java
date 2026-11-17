private final Map<String, Set<String>> existingUserIds;
private final Set<String> existingTopicIds;

class LiveNewsFeed{
    LiveNewsFeed(List<String> existingUserIds, List<String> existingTopicIds){
        existingUserIds.stream()
        .forEach(
            ele -> this.existingUserIds.add(ele)
        );

        existingTopicIds.stream()
        .forEach(
            ele -> this.existingTopicIds.add(ele)
        );
    }
}

public boolean subscribeUnsubscribeTopic(String userId, String topicId, boolean subscribe, int currentTime){
    if(!existingUserIds.contains(userId) || !existingTopicIds(topicId)){
        return false;
    }

    if (subscribe == true){
        existingUserIds.get(userId).add(topicId);
    }else{
        existingUserIds.get(userId).remove(topicId);
    }
    return true;
}

public boolean publishArticle(String articleId, String title, List<String> topics, int currentTime){

}
// List<String> getPersonalizedFeed(String userId)
// List<String> getNotifications(String userId)
// 

public class Main{
    private static final class Article{
        final String id;
        final String title;
        final int time;

        Article(String id, String title, int time){
            this.id = id;
            this.title = title;
            this.time = time;
        }

        String format(){
            return id + " | " + title + " | " time;
        }
    }

    private static final class User{
        
    }

    public static void main(String[] args){

    }
}