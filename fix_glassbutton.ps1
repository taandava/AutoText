$path='app\src\main\java\com\jmd\autotext\ui\components\BentoControls.kt'
$lines=Get-Content $path
$start=-1
for($i=0;$i -lt $lines.Count;$i++){if($lines[$i]-eq '    Surface('){$start=$i;break}}
if($start -eq -1){Write-Host('START NOT FOUND');exit}
$end=-1
for($i=$start+1;$i -lt $lines.Count;$i++){if($lines[$i]-eq '    }'){$end=$i;break}}
if($end -eq -1){Write-Host('END NOT FOUND');exit}
$newBlock = @'
    val buttonModifier = modifier
    .scale(scale)
    .clip(shape)
    .then(backgroundModifier)
    .clickable(
        interactionSource = interactionSource,
        indication = ripple(color = rippleColor),
        enabled = enabled && !isLoading,
        onClick = onClick
    )
    .padding(contentPadding)

    Box(
        modifier = buttonModifier,
        contentAlignment = Alignment.CENTER
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
'@
$newBlock = $newBlock.TrimEnd([Environment]::NewLine).Split([Environment]::NewLine)
$lines=$lines[0..($start-1)]+$newBlock+$lines[$end+1..($lines.Count-1)]
$lines|Set-Content $path -Encoding UTF8
Write-Host('GLASSBUTTON replaced: start='+ $start +' end='+ $end)
