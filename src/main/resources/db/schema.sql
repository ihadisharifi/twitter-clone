-- ---------- USERS ----------
CREATE TABLE IF NOT EXISTS users (
                                     id             SERIAL PRIMARY KEY,
                                     username       VARCHAR(50)  NOT NULL UNIQUE,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(100) NOT NULL,
    bio            TEXT,
    avatar_url     VARCHAR(500),
    banner_url     VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
    );

-- ---------- SESSIONS ----------
CREATE TABLE IF NOT EXISTS sessions (
                                        id          SERIAL PRIMARY KEY,
                                        user_id     INTEGER      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL
    );

-- ---------- TWEETS ----------
CREATE TABLE IF NOT EXISTS tweets (
                                      id             SERIAL PRIMARY KEY,
                                      author_id      INTEGER      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content        VARCHAR(1000) NOT NULL,
    reply_to_id    INTEGER      REFERENCES tweets(id) ON DELETE CASCADE,
    retweet_to_id  INTEGER      REFERENCES tweets(id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_tweets_author_id   ON tweets(author_id);
CREATE INDEX IF NOT EXISTS idx_tweets_reply_to_id ON tweets(reply_to_id);

-- ---------- FOLLOWS ----------
CREATE TABLE IF NOT EXISTS follows (
                                       id            SERIAL PRIMARY KEY,
                                       follower_id   INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id  INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (follower_id, following_id),
    CHECK (follower_id <> following_id)
    );

CREATE INDEX IF NOT EXISTS idx_follows_follower_id  ON follows(follower_id);
CREATE INDEX IF NOT EXISTS idx_follows_following_id ON follows(following_id);

-- ---------- LIKES ----------
CREATE TABLE IF NOT EXISTS likes (
                                     id        SERIAL PRIMARY KEY,
                                     user_id   INTEGER NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    tweet_id  INTEGER NOT NULL REFERENCES tweets(id)  ON DELETE CASCADE,
    UNIQUE (user_id, tweet_id)
    );

CREATE INDEX IF NOT EXISTS idx_likes_tweet_id ON likes(tweet_id);

-- ---------- MEDIA ----------
CREATE TABLE IF NOT EXISTS media (
                                     id        SERIAL PRIMARY KEY,
                                     tweet_id  INTEGER      NOT NULL REFERENCES tweets(id) ON DELETE CASCADE,
    url       VARCHAR(500) NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_media_tweet_id ON media(tweet_id);

-- ---------- HASHTAGS ----------
CREATE TABLE IF NOT EXISTS hashtags (
                                        id    SERIAL PRIMARY KEY,
                                        name  VARCHAR(100) NOT NULL UNIQUE
    );

CREATE TABLE IF NOT EXISTS tweet_hashtags (
                                              tweet_id    INTEGER NOT NULL REFERENCES tweets(id)   ON DELETE CASCADE,
    hashtag_id  INTEGER NOT NULL REFERENCES hashtags(id) ON DELETE CASCADE,
    PRIMARY KEY (tweet_id, hashtag_id)
    );

CREATE INDEX IF NOT EXISTS idx_tweet_hashtags_hashtag_id ON tweet_hashtags(hashtag_id);

