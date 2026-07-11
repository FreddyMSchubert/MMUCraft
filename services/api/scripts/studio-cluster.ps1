$ErrorActionPreference = 'Stop'

$namespace = 'mc-stack-dev'
$deployment = 'api'
$port = 4983
$kubectl = (Get-Command kubectl -ErrorAction Stop).Source

& $kubectl get deployment $deployment -n $namespace -o name | Out-Null
if ($LASTEXITCODE -ne 0) {
	throw "Unable to find deployment/$deployment in namespace $namespace."
}

& $kubectl rollout status "deployment/$deployment" -n $namespace --timeout=120s | Out-Host
if ($LASTEXITCODE -ne 0) {
	throw "deployment/$deployment did not become ready."
}

$podList = (& $kubectl get pods -n $namespace -l "app=$deployment" -o json) | ConvertFrom-Json
$pod = $podList.items |
	Where-Object { $_.status.phase -eq 'Running' -and $_.status.containerStatuses[0].ready } |
	Sort-Object { $_.metadata.creationTimestamp } -Descending |
	Select-Object -First 1
if (-not $pod) {
	throw "Unable to find a ready pod for deployment/$deployment."
}
$podName = $pod.metadata.name

$studioJob = Start-Job -ScriptBlock {
	param($kubectlPath, $namespaceName, $podName, $studioPort)
	& $kubectlPath exec -n $namespaceName "pod/$podName" -- npm run db:studio:pod -- --host 0.0.0.0 --port $studioPort
} -ArgumentList $kubectl, $namespace, $podName, $port

try {
	$ready = $false
	for ($attempt = 0; $attempt -lt 30; $attempt++) {
		if ($studioJob.State -in @('Completed', 'Failed', 'Stopped')) {
			Receive-Job -Job $studioJob
			throw 'Drizzle Studio stopped before becoming ready.'
		}

		$ErrorActionPreference = 'SilentlyContinue'
		& $kubectl exec -n $namespace "pod/$podName" -- node -e "fetch('http://127.0.0.1:$port/',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({type:'init'})}).then(response=>{if(!response.ok)process.exit(1)}).catch(()=>process.exit(1))" 2>$null
		$probeExitCode = $LASTEXITCODE
		$ErrorActionPreference = 'Stop'
		if ($probeExitCode -eq 0) {
			$ready = $true
			break
		}

		Start-Sleep -Milliseconds 500
	}

	if (-not $ready) {
		Receive-Job -Job $studioJob
		throw 'Drizzle Studio did not become ready inside the API pod.'
	}

	Write-Host ''
	Write-Host 'Drizzle Studio is connected to the Kubernetes API database.' -ForegroundColor Green
	Write-Host 'Open https://local.drizzle.studio and leave this terminal running.' -ForegroundColor Cyan
	Write-Host 'Press Ctrl+C to stop Studio and the port-forward.'
	Write-Host ''

	& $kubectl port-forward -n $namespace "pod/$podName" "${port}:${port}"
} finally {
	Stop-Job -Job $studioJob -ErrorAction SilentlyContinue
	Remove-Job -Job $studioJob -Force -ErrorAction SilentlyContinue
}
