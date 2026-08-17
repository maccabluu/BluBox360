from pathlib import Path

main = Path('app/src/main/java/uk/co/blustudio/blubox360/MainActivity.java')
text = main.read_text(encoding='utf-8')
needle = '''        panel.addView(drawerItem("☺", "Profiles & Avatars", "Switch player or make an avatar", () -> {
            closeDrawer();
            startActivity(new Intent(this, ProfileActivity.class));
        }));
'''
insert = needle + '''        panel.addView(drawerItem("●", "Friends & Online", "BluTags, friends and online status beta", () -> {
            closeDrawer();
            startActivity(new Intent(this, SocialActivity.class));
        }));
'''
if 'Friends & Online' not in text:
    if needle not in text:
        raise SystemExit('Could not find BluBox profile drawer item for social update patch.')
    text = text.replace(needle, insert, 1)
main.write_text(text, encoding='utf-8')
print('BluBox 0.17.0 Social Update navigation prepared.')
