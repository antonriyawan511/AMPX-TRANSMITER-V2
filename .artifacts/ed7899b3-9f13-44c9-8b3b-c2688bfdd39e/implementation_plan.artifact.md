# Auto Discovery Enhancements

Implement automatic saving of discovered receiver IPs and add a dedicated UI button for auto-discovery.

## Proposed Changes

### [Audio Service]

#### [MODIFY] [AudioStreamService.java](file:///C:/Users/Administrator/Links/AndroidStudioProjects/AMPLIFIER TRANSMITER PROJECT/AMPLIFIERPRO%20v021%20-%20final%20eq%20v19./app/src/main/java/com/example/amplifierpro/service/AudioStreamService.java)
- Add `ACTION_IP_DISCOVERED` constant.
- Broadcast the discovered IP when `DiscoveryClient.performDiscovery()` succeeds.

---

### [User Interface]

#### [MODIFY] [fragment_main.xml](file:///C:/Users/Administrator/Links/AndroidStudioProjects/AMPLIFIER TRANSMITER PROJECT/AMPLIFIERPRO%20v021%20-%20final%20eq%20v19./app/src/main/res/layout/fragment_main.xml)
- Replace the full-width "SCAN RECEIVER QR CODE" button with a horizontal `LinearLayout` containing two buttons:
    - **SCAN QR CODE**
    - **AUTO DISCOVER**
- Ensure both buttons have symmetric widths (weight 1) and match the existing styling.

#### [MODIFY] [MainFragment.java](file:///C:/Users/Administrator/Links/AndroidStudioProjects/AMPLIFIER TRANSMITER PROJECT/AMPLIFIERPRO%20v021%20-%20final%20eq%20v19./app/src/main/java/com/example/amplifierpro/ui/MainFragment.java)
- Listen for `ACTION_IP_DISCOVERED` broadcast.
- Automatically save the discovered IP to the "Saved Receivers" list when the broadcast is received.
- Handle the new "AUTO DISCOVER" button click:
    - Clear the manual IP input.
    - Start the streaming service (which will automatically trigger discovery if no manual IP is provided).

## Verification Plan

### Manual Verification
1.  **UI Layout**: Verify that the "SCAN QR CODE" and "AUTO DISCOVER" buttons are displayed side-by-side with equal width and consistent styling.
2.  **Auto Discovery**: Click "AUTO DISCOVER" and verify the status changes to "Mencari Receiver...".
3.  **Auto-Save**: Once a receiver is found via auto-discovery, verify that its IP appears in the "SAVED RECEIVERS" list automatically.
4.  **QR Scan**: Verify that the "SCAN QR CODE" button still functional.
