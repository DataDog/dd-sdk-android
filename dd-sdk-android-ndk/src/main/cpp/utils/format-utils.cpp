#include <string>

namespace strformat {

    template<typename... Args>
    std::string format(std::string formatter, Args... args) {
        using namespace std;
        const auto formatter_value = formatter.c_str();
        // first we compute the size of the buffer required to format this string
        // we will add 1 at the end for the end of string /0 character
        const size_t size = snprintf(nullptr, 0, formatter_value, args...) + 1;
        char buffer[size];
        snprintf(buffer, size, formatter_value, args...);
        return string(buffer, buffer + size - 1);
    }

}
