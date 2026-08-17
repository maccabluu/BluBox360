from pathlib import Path

path = Path('app/src/main/java/uk/co/blustudio/blubox360/SocialActivity.java')
text = path.read_text(encoding='utf-8')

text = text.replace('title.addView(label("Friends & Online", 22, Color.WHITE, true));\n        title.addView(label("BluBox Social Beta", 11, getColor(R.color.cyan), true));',
'''title.addView(label("BluBox Live", 22, Color.WHITE, true));
        title.addView(label("Friends, presence and account beta", 11, getColor(R.color.cyan), true));''')

text = text.replace('private SocialStore socialStore;\n    private ProfileStore.Profile activeProfile;',
'''private SocialStore socialStore;
    private BluBoxLiveStore liveStore;
    private ProfileStore.Profile activeProfile;''')

text = text.replace('socialStore = new SocialStore(this);\n        activeProfile = profileStore.getActive();',
'''socialStore = new SocialStore(this);
        liveStore = new BluBoxLiveStore(this);
        activeProfile = profileStore.getActive();''')

marker = '        LinearLayout onlineCard = card();\n'
insert = '''        LinearLayout liveCard = card();
        liveCard.addView(label("BLUBOX LIVE ACCOUNT", 12, getColor(R.color.cyan), true));
        BluBoxLiveStore.LiveAccount liveAccount = liveStore.account(activeProfile.id);
        if (liveAccount == null) {
            liveCard.addView(label("Create your BluBox Live identity", 17, Color.WHITE, true));
            liveCard.addView(label("Your BluBox Live account stays linked to this profile. It is separate from Microsoft and does not use an Xbox password.",
                    11, getColor(R.color.muted), false));
            Button createLive = button("Create BluBox Live Account", true);
            createLive.setOnClickListener(v -> {
                liveStore.createAccount(activeProfile.id);
                Toast.makeText(this, "BluBox Live account created", Toast.LENGTH_SHORT).show();
                render();
            });
            LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            createParams.topMargin = dp(12);
            liveCard.addView(createLive, createParams);
        } else {
            liveCard.addView(label(activeProfile.name, 19, Color.WHITE, true));
            liveCard.addView(label("Account ID: " + liveAccount.accountId, 12, getColor(R.color.cyan), true));
            liveCard.addView(label(liveStore.serviceStatus(), 11, getColor(R.color.muted), false));
            Button copyAccount = button("Copy Account ID", false);
            copyAccount.setOnClickListener(v -> copyLiveAccountId(liveAccount.accountId));
            LinearLayout.LayoutParams copyAccountParams = new LinearLayout.LayoutParams(dp(170), dp(48));
            copyAccountParams.topMargin = dp(10);
            liveCard.addView(copyAccount, copyAccountParams);
        }
        content.addView(liveCard, matchWrap(dp(0), dp(12)));

'''
if marker not in text:
    raise SystemExit('Could not find online card marker')
text = text.replace(marker, insert + marker, 1)

text = text.replace('"This beta adds friends, BluTags, matching profile/online names and presence controls. Internet friend syncing and game invites still need the BluBox online server before they go live."',
'"BluBox Live 0.17.2 adds a persistent BluBox account identity alongside friends, BluTags, matching names and presence. Cross-device friend requests, messages and game invites will switch on after the BluBox server is deployed."')

method_marker = '    private void copyBluTag(String tag) {'
method = '''    private void copyLiveAccountId(String accountId) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("BluBox Live Account ID", accountId));
        Toast.makeText(this, "BluBox Live Account ID copied", Toast.LENGTH_SHORT).show();
    }

'''
if method_marker not in text:
    raise SystemExit('Could not find clipboard method marker')
text = text.replace(method_marker, method + method_marker, 1)

path.write_text(text, encoding='utf-8')
print('Prepared BluBox Live 0.17.2 UI')
