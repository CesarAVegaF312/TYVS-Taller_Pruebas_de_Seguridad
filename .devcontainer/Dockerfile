# Imagen base del devcontainer.
#
# La imagen de Microsoft trae configurado el repositorio apt de Yarn, cuya
# clave GPG ya no es valida: cualquier `apt-get update` posterior (por ejemplo,
# el de la feature docker-in-docker) falla con "NO_PUBKEY". Este taller no usa
# Yarn, asi que se quita esa fuente antes de instalar las features.
FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm
RUN grep -rl yarnpkg /etc/apt/sources.list.d/ | xargs -r rm -f
