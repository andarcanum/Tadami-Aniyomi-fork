import re
import sys

def fix_achievement_in_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Pattern 1: Achievement( id = "...", type = AchievementType.XXX, category = ...)
    # Replace with: Achievement( id = "...", title = "...", type = ...
    pattern1 = r'(Achievement\(\s*)id = "([^"]+)"([,\s]+type = AchievementType\.[A-Z_]+,[\s\n]+category =)'
    replacement1 = r'\1id = "\2",\n            title = "\2"\3'
    content = re.sub(pattern1, replacement1, content)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed {file_path}")

if __name__ == "__main__":
    files = [
        "data/src/test/java/tachiyomi/data/achievement/handler/checkers/FeatureBasedAchievementCheckerTest.kt",
        "data/src/test/java/tachiyomi/data/achievement/handler/checkers/TimeBasedAchievementCheckerTest.kt"
    ]
    for f in files:
        fix_achievement_in_file(f)
