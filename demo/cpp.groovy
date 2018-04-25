def label = "gcc-riscv-${UUID.randomUUID().toString()}"
podTemplate(name: 'test', label: label,
    containers: [
        containerTemplate(name: 'gcc-riscv',
            image: 'onenashev/gcc-riscv:6.4-rc',
            ttyEnabled: true, command: 'cat')]) {
    node(label) {
        checkout scm
        sh "make clean test"
        step([$class: 'TapPublisher', testResults: 'output/test/report.tap', ...])
    }
}


docker.image('onenashev/gcc-riscv:6.4-rc').inside {
    checkout scm
    sh "make clean test"
}

