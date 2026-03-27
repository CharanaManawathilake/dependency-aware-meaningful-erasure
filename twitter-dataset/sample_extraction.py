import pandas as pd
import os

os.makedirs("samples", exist_ok=True)

profiles = pd.read_csv("profile.csv")
sample_profiles = profiles.sample(n=2, random_state=42)
sample_profiles.to_csv("samples/sample_profile.csv", index=False)

profid_set = set(sample_profiles["profid"])

posts = pd.read_csv("posts.csv")
sample_posts = posts[posts["profid"].isin(profid_set)].copy()
# Replace newlines in postdata with spaces so they don't break CSV formatting
sample_posts["postdata"] = sample_posts["postdata"].str.replace(r'[\r\n]+', ' ', regex=True)
sample_posts.to_csv("samples/sample_posts.csv", index=False)

tweetid_set = set(sample_posts["tweetid"])

# Extract posts insertion times correctly
posts_insertion = pd.read_csv("posts_insertiontime.csv")
sample_posts_insertion = posts_insertion[
    posts_insertion["insertionkey"].isin(tweetid_set)
]
sample_posts_insertion.to_csv("samples/sample_posts_insertiontime.csv", index=False)

# Extract profile insertion times correctly
profile_insertion = pd.read_csv("profile_insertiontime.csv")
sample_profile_insertion = profile_insertion[
    profile_insertion["insertionkey"].isin(profid_set)
]
sample_profile_insertion.to_csv("samples/sample_profile_insertiontime.csv", index=False)
