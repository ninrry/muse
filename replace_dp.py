import os, re

mapping = {
    r'2\.dp': 'AppSpacing.xxxs',
    r'4\.dp': 'AppSpacing.xxs',
    r'8\.dp': 'AppSpacing.xs',
    r'12\.dp': 'AppSpacing.sm',
    r'16\.dp': 'AppSpacing.md',
    r'24\.dp': 'AppSpacing.lg',
    r'32\.dp': 'AppSpacing.xlg',
    r'48\.dp': 'AppSpacing.xxlg',
    r'64\.dp': 'AppSpacing.xxxlg',
    r'128\.dp': 'AppSpacing.massive'
}

def process_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    orig = content
    for pattern, replacement in mapping.items():
        content = re.sub(r'\b' + pattern + r'\b', replacement, content)
    
    if orig != content:
        # Add import if missing
        if 'luzzr.muse.ui.theme.AppSpacing' not in content:
            # find first import and insert after it
            content = re.sub(r'(import .*?\n)', r'\1import luzzr.muse.ui.theme.AppSpacing\n', content, count=1)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
            print(f"Updated {path}")
            
for root, _, files in os.walk(r'D:\workspapce\codexworkspace\muse-main\app\src\main\java\luzzr\muse\ui'):
    for file in files:
        if file.endswith('.kt') and file not in ['Spacing.kt', 'Shape.kt', 'Theme.kt', 'Color.kt', 'Type.kt']:
            process_file(os.path.join(root, file))
