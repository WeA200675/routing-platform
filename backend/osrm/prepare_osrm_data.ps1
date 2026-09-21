param(
    [Parameter(Mandatory=$true)]
    [string]$Pbf
)
$ErrorActionPreference = "Stop"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Data = Join-Path $Here "data"
New-Item -ItemType Directory -Force -Path $Data | Out-Null
Copy-Item -Force (Resolve-Path $Pbf) (Join-Path $Data "region.osm.pbf")
$Image = "ghcr.io/project-osrm/osrm-backend:v5.27.1"
docker run --rm -v "${Data}:/data" $Image osrm-extract -p /opt/car.lua /data/region.osm.pbf
docker run --rm -v "${Data}:/data" $Image osrm-partition /data/region.osrm
docker run --rm -v "${Data}:/data" $Image osrm-customize /data/region.osrm
Write-Host "Prepared $Data\region.osrm"
